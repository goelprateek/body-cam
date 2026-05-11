package com.kriyanshtech.bodycam.recording.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptSegment;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingTranscriptRepository;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class RecordingTranscriptService {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptService.class);
    private static final String ENGINE_NAME = "vosk";
    private static final String MODEL_NAME = "alphacep/kaldi-en";
    private static final int TARGET_SAMPLE_RATE = 16_000;
    private static final int AUDIO_CHUNK_SIZE_BYTES = TARGET_SAMPLE_RATE * 2 / 4;

    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingTranscriptRepository recordingTranscriptRepository;
    private final ObjectStorageService objectStorageService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RecordingTranscriptService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingTranscriptRepository recordingTranscriptRepository,
            ObjectStorageService objectStorageService,
            AppProperties appProperties,
            ObjectMapper objectMapper
    ) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingTranscriptRepository = recordingTranscriptRepository;
        this.objectStorageService = objectStorageService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Transactional(readOnly = true)
    public RecordingTranscriptResponse getTranscript(UUID recordingId) {
        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));

        return recordingTranscriptRepository.findByRecording_Id(recordingId)
                .map(this::map)
                .orElseGet(() -> notRequested(recording));
    }

    @Transactional
    public RecordingTranscriptResponse generateTranscript(UUID recordingId) {
        if (!appProperties.transcript().enabled()) {
            throw new IllegalStateException("Transcript generation is disabled in backend configuration");
        }

        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));

        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recordingId)
                .orElseGet(() -> createTranscript(recording));

        Instant startedAt = Instant.now();
        transcript.setStatus(RecordingTranscriptStatus.PROCESSING);
        transcript.setEngine(ENGINE_NAME);
        transcript.setModel(MODEL_NAME);
        transcript.setLanguageCode(appProperties.transcript().languageCode());
        transcript.setErrorMessage(null);
        transcript.setStartedAt(startedAt);
        transcript.setCompletedAt(null);
        transcript.setUpdatedAt(startedAt);
        transcript.replaceSegments(List.of());
        recordingTranscriptRepository.save(transcript);
        log.info("Transcript generation started recordingId={} transcriptId={} objectKey={}",
                recordingId, transcript.getId(), recording.getObjectKey());

        Path sourceVideoPath = null;
        Path audioPath = null;
        try {
            sourceVideoPath = downloadRecordingToTempFile(recording);
            audioPath = extractMonoPcmAudio(sourceVideoPath, transcript.getId());
            VoskTranscriptResult transcription = transcribeWithVosk(audioPath, transcript.getId(), recordingId);

            transcript.replaceSegments(toSegments(transcript, transcription.segments(), Instant.now()));
            transcript.setFullText(transcription.fullText());
            transcript.setStatus(RecordingTranscriptStatus.READY);
            transcript.setCompletedAt(Instant.now());
            transcript.setUpdatedAt(Instant.now());
            RecordingTranscript savedTranscript = recordingTranscriptRepository.save(transcript);
            log.info("Transcript generation completed recordingId={} transcriptId={} segmentCount={}",
                    recordingId, savedTranscript.getId(), savedTranscript.getSegments().size());
            return map(savedTranscript);
        } catch (Exception exception) {
            transcript.replaceSegments(List.of());
            transcript.setStatus(RecordingTranscriptStatus.FAILED);
            transcript.setErrorMessage(exception.getMessage());
            transcript.setCompletedAt(Instant.now());
            transcript.setUpdatedAt(Instant.now());
            recordingTranscriptRepository.save(transcript);
            log.error("Transcript generation failed recordingId={} transcriptId={}",
                    recordingId, transcript.getId(), exception);
            return map(transcript);
        } finally {
            deleteIfExists(sourceVideoPath);
            deleteIfExists(audioPath);
        }
    }

    @Transactional(readOnly = true)
    public String buildSubtitleVtt(UUID recordingId) {
        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recordingId)
                .orElseThrow(() -> new NotFoundException("Transcript not found for recording: " + recordingId));

        if (transcript.getStatus() != RecordingTranscriptStatus.READY || transcript.getSegments().isEmpty()) {
            throw new IllegalStateException("Transcript subtitles are not available yet");
        }

        StringBuilder builder = new StringBuilder("WEBVTT\n\n");
        for (RecordingTranscriptSegment segment : transcript.getSegments()) {
            builder.append(segment.getSegmentIndex() + 1).append('\n')
                    .append(formatVttTimestamp(segment.getStartSeconds()))
                    .append(" --> ")
                    .append(formatVttTimestamp(segment.getEndSeconds()))
                    .append('\n')
                    .append(HtmlUtils.htmlEscape(segment.getText()))
                    .append("\n\n");
        }
        return builder.toString();
    }

    private RecordingTranscriptResponse notRequested(RecordingAsset recording) {
        log.info("Transcript not yet requested for recordingId={}", recording.getId());
        return new RecordingTranscriptResponse(
                null,
                recording.getId(),
                RecordingTranscriptStatus.NOT_REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private RecordingTranscript createTranscript(RecordingAsset recording) {
        RecordingTranscript transcript = new RecordingTranscript();
        Instant now = Instant.now();
        transcript.setId(UUID.randomUUID());
        transcript.setRecording(recording);
        transcript.setStatus(RecordingTranscriptStatus.PENDING);
        transcript.setCreatedAt(now);
        transcript.setUpdatedAt(now);
        return transcript;
    }

    private Path downloadRecordingToTempFile(RecordingAsset recording) throws IOException {
        String extension = extensionFor(recording.getObjectKey());
        Path tempFile = Files.createTempFile("bodycam-recording-" + recording.getId(), extension);
        try (InputStream inputStream = objectStorageService.download(recording.getObjectKey())) {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private Path extractMonoPcmAudio(Path sourceVideoPath, UUID transcriptId) throws IOException, InterruptedException {
        Path outputAudioPath = Files.createTempFile("bodycam-transcript-audio-" + transcriptId, ".wav");
        List<String> command = List.of(
                appProperties.transcript().ffmpegCommand(),
                "-y",
                "-i",
                sourceVideoPath.toString(),
                "-vn",
                "-ac",
                "1",
                "-ar",
                Integer.toString(TARGET_SAMPLE_RATE),
                "-acodec",
                "pcm_s16le",
                outputAudioPath.toString()
        );
        log.info("Extracting transcript audio with command={} transcriptId={}", command, transcriptId);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("ffmpeg audio extraction failed: " + trim(processOutput));
        }
        return outputAudioPath;
    }

    private VoskTranscriptResult transcribeWithVosk(Path wavPath, UUID transcriptId, UUID recordingId) throws Exception {
        AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(wavPath.toFile());
        if (audioFileFormat.getType() != AudioFileFormat.Type.WAVE) {
            throw new IllegalStateException("Expected ffmpeg to generate a WAV file");
        }

        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavPath.toFile())) {
            float sampleRate = audioInputStream.getFormat().getSampleRate();
            if (sampleRate <= 0) {
                throw new IllegalStateException("Unable to detect WAV sample rate for transcript generation");
            }

            URI uri = URI.create(appProperties.transcript().voskUrl());
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
                    true
            ).join();

            List<VoskSegmentPayload> segments = new ArrayList<>();
            byte[] buffer = new byte[AUDIO_CHUNK_SIZE_BYTES];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                if (bytesRead == 0) {
                    continue;
                }
                webSocket.sendBinary(ByteBuffer.wrap(buffer, 0, bytesRead), true).join();
                collectSegment(listener.awaitMessage(), segments);
            }

            webSocket.sendText("{\"eof\":1}", true).join();
            collectSegment(listener.awaitMessage(), segments);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();

            String fullText = segments.stream()
                    .map(VoskSegmentPayload::text)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");

            log.info("Transcribed recordingId={} transcriptId={} segmentCount={} sampleRate={}",
                    recordingId, transcriptId, segments.size(), sampleRate);
            return new VoskTranscriptResult(fullText, segments);
        }
    }

    private void collectSegment(String payload, List<VoskSegmentPayload> segments) throws IOException {
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
                segments.add(new VoskSegmentPayload(
                        decimal(firstWord.path("start").asDouble()),
                        decimal(lastWord.path("end").asDouble()),
                        node.path("text").asText(),
                        confidence
                ));
            } else {
                int segmentIndex = segments.size();
                BigDecimal start = decimal(segmentIndex * 4.0);
                BigDecimal end = decimal((segmentIndex + 1) * 4.0);
                segments.add(new VoskSegmentPayload(start, end, node.path("text").asText(), null));
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

    private List<RecordingTranscriptSegment> toSegments(
            RecordingTranscript transcript,
            List<VoskSegmentPayload> payloads,
            Instant createdAt
    ) {
        List<RecordingTranscriptSegment> segments = new ArrayList<>();
        for (int index = 0; index < payloads.size(); index++) {
            VoskSegmentPayload payload = payloads.get(index);
            RecordingTranscriptSegment segment = new RecordingTranscriptSegment();
            segment.setId(UUID.randomUUID());
            segment.setTranscript(transcript);
            segment.setSegmentIndex(index);
            segment.setStartSeconds(payload.startSeconds());
            segment.setEndSeconds(payload.endSeconds());
            segment.setText(payload.text());
            segment.setConfidence(payload.confidence());
            segment.setCreatedAt(createdAt);
            segments.add(segment);
        }
        return segments;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String extensionFor(String objectKey) {
        int dotIndex = objectKey.lastIndexOf('.');
        return dotIndex >= 0 ? objectKey.substring(dotIndex) : ".bin";
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary transcript file {}", path, exception);
        }
    }

    private String trim(String processOutput) {
        String normalized = processOutput == null ? "" : processOutput.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String formatVttTimestamp(BigDecimal secondsValue) {
        long millis = secondsValue.multiply(BigDecimal.valueOf(1000)).longValue();
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long milliseconds = millis % 1000;
        return "%02d:%02d:%02d.%03d".formatted(hours, minutes, seconds, milliseconds);
    }

    private RecordingTranscriptResponse map(RecordingTranscript transcript) {
        return new RecordingTranscriptResponse(
                transcript.getId(),
                transcript.getRecording().getId(),
                transcript.getStatus(),
                transcript.getEngine(),
                transcript.getModel(),
                transcript.getLanguageCode(),
                transcript.getFullText(),
                transcript.getErrorMessage(),
                transcript.getStartedAt(),
                transcript.getCompletedAt(),
                transcript.getCreatedAt(),
                transcript.getUpdatedAt(),
                transcript.getSegments().stream().map(this::mapSegment).toList()
        );
    }

    private RecordingTranscriptSegmentResponse mapSegment(RecordingTranscriptSegment segment) {
        return new RecordingTranscriptSegmentResponse(
                segment.getId(),
                segment.getSegmentIndex(),
                segment.getStartSeconds(),
                segment.getEndSeconds(),
                segment.getText(),
                segment.getConfidence()
        );
    }

    private record VoskSegmentPayload(
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            String text,
            BigDecimal confidence
    ) {
    }

    private record VoskTranscriptResult(
            String fullText,
            List<VoskSegmentPayload> segments
    ) {
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
