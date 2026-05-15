package com.kriyanshtech.bodycam.recording.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kriyanshtech.bodycam.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class VoskRecordingTranscriptEngine implements RecordingTranscriptEngine {
    private static final Logger log = LoggerFactory.getLogger(VoskRecordingTranscriptEngine.class);
    private static final String ENGINE_NAME = "vosk";
    private static final String MODEL_NAME = "alphacep/kaldi-en";
    private static final int AUDIO_CHUNK_SIZE_BYTES = 16_000 * 2 / 4;
    private static final String EOF_MESSAGE = "{\"eof\" : 1}";

    private final RecordingTranscriptAudioExtractor audioExtractor;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public VoskRecordingTranscriptEngine(
            RecordingTranscriptAudioExtractor audioExtractor,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.audioExtractor = audioExtractor;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String key() {
        return ENGINE_NAME;
    }

    @Override
    public String label() {
        return "Vosk";
    }

    @Override
    public String configuredEndpoint() {
        return appProperties.transcript().voskUrl();
    }

    @Override
    public RecordingTranscriptGenerationResult generate(Path sourceVideoPath, UUID recordingId, UUID transcriptId)
            throws Exception {
        log.info("Starting transcript engine recordingId={} transcriptId={} engine={} endpoint={}",
                recordingId, transcriptId, ENGINE_NAME, configuredEndpoint());
        ExtractedTranscriptAudio extractedAudio = audioExtractor.extractMonoPcmAudio(sourceVideoPath, transcriptId);
        try {
            return transcribe(extractedAudio, recordingId, transcriptId);
        } catch (Exception exception) {
            log.error("Transcript engine failed recordingId={} transcriptId={} engine={}",
                    recordingId, transcriptId, ENGINE_NAME, exception);
            throw exception;
        } finally {
            RecordingTranscriptSupport.deleteIfExists(log, extractedAudio.wavPath(), "audio");
        }
    }

    private RecordingTranscriptGenerationResult transcribe(
            ExtractedTranscriptAudio extractedAudio,
            UUID recordingId,
            UUID transcriptId) throws Exception {
        Path wavPath = extractedAudio.wavPath();
        AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(wavPath.toFile());
        if (audioFileFormat.getType() != AudioFileFormat.Type.WAVE) {
            throw new IllegalStateException("Expected embedded media extractor to generate a WAV file");
        }

        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavPath.toFile())) {
            float sampleRate = extractedAudio.sampleRate();
            if (sampleRate <= 0) {
                sampleRate = audioInputStream.getFormat().getSampleRate();
            }
            if (sampleRate <= 0) {
                throw new IllegalStateException("Unable to detect WAV sample rate for transcript generation");
            }

            URI uri = URI.create(configuredEndpoint());
            log.info("Connecting to transcript engine recordingId={} transcriptId={} engine={} uri={} sampleRate={}",
                    recordingId, transcriptId, ENGINE_NAME, uri, sampleRate);
            VoskMessageCollector listener = new VoskMessageCollector();
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, listener)
                    .join();

            webSocket.sendText(
                    objectMapper.createObjectNode()
                            .set("config", objectMapper.createObjectNode()
                                    .put("sample_rate", sampleRate)
                                    .put("words", 1)
                                    .put("max_alternatives", 0))
                            .toString(),
                    true).join();

            List<TranscriptSegmentPayload> segments = new ArrayList<>();
            byte[] buffer = new byte[AUDIO_CHUNK_SIZE_BYTES];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                if (bytesRead == 0) {
                    continue;
                }
                webSocket.sendBinary(ByteBuffer.wrap(buffer, 0, bytesRead), true).join();
                collectSegment(listener.awaitMessage(), segments);
            }

            log.info("Sending transcript engine EOF recordingId={} transcriptId={} engine={}",
                    recordingId, transcriptId, ENGINE_NAME);
            webSocket.sendText(EOF_MESSAGE, true).join();
            collectSegment(listener.awaitMessage(), segments);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();

            String fullText = RecordingTranscriptSupport.joinSegmentText(segments);

            log.info("Transcribed recordingId={} transcriptId={} engine={} segmentCount={} sampleRate={}",
                    recordingId, transcriptId, ENGINE_NAME, segments.size(), sampleRate);
            return new RecordingTranscriptGenerationResult(
                    ENGINE_NAME,
                    MODEL_NAME,
                    appProperties.transcript().languageCode(),
                    fullText,
                    segments);
        }
    }

    private void collectSegment(String payload, List<TranscriptSegmentPayload> segments) throws IOException {
        JsonNode node = objectMapper.readTree(payload);
        if (node.hasNonNull("error") && !node.path("error").asText().isBlank()) {
            throw new IllegalStateException("Vosk transcription failed: " + node.path("error").asText());
        }
        if (node.hasNonNull("text") && !node.path("text").asText().isBlank()) {
            JsonNode wordsNode = node.path("result");
            if (wordsNode.isArray() && !wordsNode.isEmpty()) {
                JsonNode firstWord = wordsNode.get(0);
                JsonNode lastWord = wordsNode.get(wordsNode.size() - 1);
                BigDecimal confidence = averageConfidence(wordsNode);
                segments.add(new TranscriptSegmentPayload(
                        RecordingTranscriptSupport.decimal(firstWord.path("start").asDouble()),
                        RecordingTranscriptSupport.decimal(lastWord.path("end").asDouble()),
                        node.path("text").asText(),
                        confidence));
            } else {
                int segmentIndex = segments.size();
                BigDecimal start = RecordingTranscriptSupport.decimal(segmentIndex * 4.0);
                BigDecimal end = RecordingTranscriptSupport.decimal((segmentIndex + 1) * 4.0);
                segments.add(new TranscriptSegmentPayload(start, end, node.path("text").asText(), null));
            }
        }
    }

    private BigDecimal averageConfidence(JsonNode wordsNode) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (JsonNode wordNode : wordsNode) {
            if (wordNode.has("conf")) {
                total = total.add(BigDecimal.valueOf(wordNode.path("conf").asDouble()));
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private static final class VoskMessageCollector implements WebSocket.Listener {
        private final StringBuilder currentMessage = new StringBuilder();
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                queue.offer(currentMessage.toString());
                currentMessage.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            queue.offer("{\"error\":\"" + error.getMessage() + "\"}");
        }

        String awaitMessage() {
            try {
                String message = queue.poll(30, TimeUnit.SECONDS);
                if (message == null) {
                    throw new IllegalStateException("Timed out waiting for Vosk transcription response");
                }
                return message;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Vosk transcription response", exception);
            }
        }
    }
}
