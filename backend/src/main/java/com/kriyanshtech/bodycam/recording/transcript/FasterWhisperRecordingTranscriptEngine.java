package com.kriyanshtech.bodycam.recording.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kriyanshtech.bodycam.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FasterWhisperRecordingTranscriptEngine implements RecordingTranscriptEngine {
    private static final Logger log = LoggerFactory.getLogger(FasterWhisperRecordingTranscriptEngine.class);
    private static final String ENGINE_NAME = "faster-whisper";

    private final RecordingTranscriptAudioExtractor audioExtractor;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FasterWhisperRecordingTranscriptEngine(
            RecordingTranscriptAudioExtractor audioExtractor,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.audioExtractor = audioExtractor;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public String key() {
        return ENGINE_NAME;
    }

    @Override
    public RecordingTranscriptGenerationResult generate(Path sourceVideoPath, UUID recordingId, UUID transcriptId)
            throws Exception {
        AppProperties.FasterWhisper config = requiredConfig();
        log.info("Starting transcript engine recordingId={} transcriptId={} engine={} endpoint={} model={} task={}",
                recordingId, transcriptId, ENGINE_NAME, config.url(), config.model(), config.task());
        ExtractedTranscriptAudio extractedAudio = audioExtractor.extractMonoPcmAudio(sourceVideoPath, transcriptId);
        try {
            return transcribe(extractedAudio, recordingId, transcriptId, config);
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
            UUID transcriptId,
            AppProperties.FasterWhisper config) throws Exception {
        String boundary = "bodycam-transcript-" + transcriptId;
        log.info("Posting transcript audio to engine recordingId={} transcriptId={} engine={} uri={} timeoutSeconds={}",
                recordingId, transcriptId, ENGINE_NAME, config.url(), config.timeoutSeconds());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.url()))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(buildMultipartBody(boundary, extractedAudio.wavPath(), config))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("faster-whisper transcription failed: HTTP " + response.statusCode() + " - "
                    + RecordingTranscriptSupport.trimMessage(response.body()));
        }

        JsonNode payload = objectMapper.readTree(response.body());
        if (payload.hasNonNull("error")) {
            JsonNode errorNode = payload.path("error");
            if (errorNode.isTextual()) {
                throw new IllegalStateException("faster-whisper transcription failed: " + errorNode.asText());
            }
            if (errorNode.hasNonNull("message")) {
                throw new IllegalStateException(
                        "faster-whisper transcription failed: " + errorNode.path("message").asText());
            }
        }

        List<TranscriptSegmentPayload> segments = parseSegments(payload);
        String fullText = payload.path("text").asText("");
        if ((fullText == null || fullText.isBlank()) && !segments.isEmpty()) {
            fullText = RecordingTranscriptSupport.joinSegmentText(segments);
        }

        String languageCode = payload.path("language").asText(appProperties.transcript().languageCode());
        String model = payload.path("model").asText(config.model());
        log.info("Transcribed recordingId={} transcriptId={} engine={} segmentCount={} language={} model={}",
                recordingId, transcriptId, ENGINE_NAME, segments.size(), languageCode, model);
        return new RecordingTranscriptGenerationResult(
                ENGINE_NAME,
                model,
                languageCode,
                fullText,
                segments);
    }

    private AppProperties.FasterWhisper requiredConfig() {
        AppProperties.FasterWhisper config = appProperties.transcript().fasterWhisper();
        if (config == null || config.url() == null || config.url().isBlank()) {
            throw new IllegalStateException(
                    "faster-whisper transcript engine is missing TRANSCRIPT_FASTER_WHISPER_URL");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IllegalStateException(
                    "faster-whisper transcript engine is missing TRANSCRIPT_FASTER_WHISPER_MODEL");
        }
        if (config.task() == null || config.task().isBlank()) {
            throw new IllegalStateException(
                    "faster-whisper transcript engine is missing TRANSCRIPT_FASTER_WHISPER_TASK");
        }
        if (config.timeoutSeconds() <= 0) {
            throw new IllegalStateException("faster-whisper transcript engine timeout must be positive");
        }
        return config;
    }

    private BodyPublisher buildMultipartBody(String boundary, Path wavPath, AppProperties.FasterWhisper config)
            throws IOException {
        return HttpRequest.BodyPublishers.concat(
                textPart(boundary, "model", config.model()),
                textPart(boundary, "language", appProperties.transcript().languageCode()),
                textPart(boundary, "task", config.task()),
                textPart(boundary, "response_format", "verbose_json"),
                textPart(boundary, "timestamp_granularities[]", "segment"),
                fileHeaderPart(boundary),
                HttpRequest.BodyPublishers.ofFile(wavPath),
                HttpRequest.BodyPublishers
                        .ofByteArray(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8)));
    }

    private BodyPublisher textPart(String boundary, String name, String value) {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        return HttpRequest.BodyPublishers.ofByteArray(part.getBytes(StandardCharsets.UTF_8));
    }

    private BodyPublisher fileHeaderPart(String boundary) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"transcript.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        return HttpRequest.BodyPublishers.ofByteArray(header.getBytes(StandardCharsets.UTF_8));
    }

    private List<TranscriptSegmentPayload> parseSegments(JsonNode payload) {
        List<TranscriptSegmentPayload> segments = new ArrayList<>();
        JsonNode segmentsNode = payload.path("segments");
        if (!segmentsNode.isArray()) {
            return segments;
        }

        for (JsonNode segmentNode : segmentsNode) {
            String text = segmentNode.path("text").asText("");
            if (text.isBlank()) {
                continue;
            }

            BigDecimal start = RecordingTranscriptSupport.decimal(segmentNode.path("start").asDouble());
            BigDecimal end = RecordingTranscriptSupport.decimal(segmentNode.path("end").asDouble());
            BigDecimal confidence = parseConfidence(segmentNode);
            segments.add(new TranscriptSegmentPayload(start, end, text, confidence));
        }
        return segments;
    }

    private BigDecimal parseConfidence(JsonNode segmentNode) {
        if (segmentNode.has("confidence") && !segmentNode.path("confidence").isNull()) {
            return RecordingTranscriptSupport.decimal(segmentNode.path("confidence").asDouble());
        }
        if (segmentNode.has("probability") && !segmentNode.path("probability").isNull()) {
            return RecordingTranscriptSupport.decimal(segmentNode.path("probability").asDouble());
        }
        if (segmentNode.has("avg_logprob") && !segmentNode.path("avg_logprob").isNull()) {
            double probability = Math.exp(segmentNode.path("avg_logprob").asDouble());
            return RecordingTranscriptSupport.decimal(Math.max(0.0d, Math.min(1.0d, probability)));
        }
        return null;
    }
}
