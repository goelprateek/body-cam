package com.kriyanshtech.bodycam.recording.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kriyanshtech.bodycam.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FasterWhisperRecordingTranscriptEngine implements RecordingTranscriptEngine {
    private static final Logger log = LoggerFactory.getLogger(FasterWhisperRecordingTranscriptEngine.class);
    private static final String ENGINE_NAME = "faster-whisper";

    private final RecordingTranscriptAudioExtractor audioExtractor;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public FasterWhisperRecordingTranscriptEngine(
            RecordingTranscriptAudioExtractor audioExtractor,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.audioExtractor = audioExtractor;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return ENGINE_NAME;
    }

    @Override
    public String label() {
        return "Faster Whisper";
    }

    @Override
    public String configuredEndpoint() {
        AppProperties.FasterWhisper config = appProperties.transcript().fasterWhisper();
        return config != null ? config.url() : null;
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
        log.info("Posting transcript audio to engine recordingId={} transcriptId={} engine={} uri={} timeoutSeconds={}",
                recordingId, transcriptId, ENGINE_NAME, config.url(), config.timeoutSeconds());
        String responseBody = postMultipartRequest(extractedAudio.wavPath(), config);
        JsonNode payload = objectMapper.readTree(responseBody);
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

    private String postMultipartRequest(Path wavPath, AppProperties.FasterWhisper config) {
        RestTemplate restTemplate = createRestTemplate(config.timeoutSeconds());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", config.model());
        body.add("language", normalizedLanguageCode(appProperties.transcript().languageCode()));
        body.add("task", config.task());
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "segment");
        body.add("file", audioFilePart(wavPath));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    config.url(),
                    new HttpEntity<>(body, headers),
                    String.class);
            return response.getBody() == null ? "" : response.getBody();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalStateException(
                    "faster-whisper transcription failed: HTTP " + exception.getStatusCode().value() + " - "
                            + RecordingTranscriptSupport.trimMessage(exception.getResponseBodyAsString()),
                    exception);
        }
    }

    private HttpEntity<FileSystemResource> audioFilePart(Path wavPath) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType("audio/wav"));
        return new HttpEntity<>(new FileSystemResource(wavPath), partHeaders);
    }

    private RestTemplate createRestTemplate(long timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(Duration.ofSeconds(timeoutSeconds).toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return new RestTemplate(requestFactory);
    }

    private String normalizedLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return "auto";
        }
        String normalized = languageCode.trim();
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex < 0) {
            separatorIndex = normalized.indexOf('_');
        }
        if (separatorIndex > 0) {
            normalized = normalized.substring(0, separatorIndex);
        }
        return normalized.toLowerCase(Locale.ROOT);
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
