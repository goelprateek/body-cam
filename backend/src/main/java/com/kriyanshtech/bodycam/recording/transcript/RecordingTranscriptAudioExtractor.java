package com.kriyanshtech.bodycam.recording.transcript;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class RecordingTranscriptAudioExtractor {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptAudioExtractor.class);
    private static final int TARGET_SAMPLE_RATE = 16_000;
    private static final int TARGET_CHANNELS = 1;

    public ExtractedTranscriptAudio extractMonoPcmAudio(Path sourceVideoPath, UUID transcriptId) throws Exception {
        Path outputAudioPath = Files.createTempFile("bodycam-transcript-audio-" + transcriptId, ".wav");
        log.info("Starting transcript audio extraction transcriptId={} source={}", transcriptId, sourceVideoPath);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(sourceVideoPath.toFile())) {
            grabber.start();
            if (grabber.getAudioChannels() <= 0) {
                throw new IllegalStateException("Recording does not contain an audio stream for transcript generation");
            }

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputAudioPath.toFile(), TARGET_CHANNELS)) {
                recorder.setFormat("wav");
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);
                recorder.setSampleRate(TARGET_SAMPLE_RATE);
                recorder.setAudioChannels(TARGET_CHANNELS);
                recorder.setAudioBitrate(TARGET_SAMPLE_RATE * 16);
                recorder.start();

                boolean wroteSamples = false;
                Frame frame;
                while ((frame = grabber.grabSamples()) != null) {
                    if (frame.samples == null) {
                        continue;
                    }

                    int sourceSampleRate = frame.sampleRate > 0 ? frame.sampleRate : grabber.getSampleRate();
                    int sourceChannels = frame.audioChannels > 0 ? frame.audioChannels : grabber.getAudioChannels();
                    recorder.recordSamples(sourceSampleRate, sourceChannels, frame.samples);
                    wroteSamples = true;
                }

                if (!wroteSamples) {
                    throw new IllegalStateException("Unable to extract audio samples for transcript generation");
                }
            }

            log.info("Extracted transcript audio transcriptId={} sampleRate={}", transcriptId, TARGET_SAMPLE_RATE);
            return new ExtractedTranscriptAudio(outputAudioPath, TARGET_SAMPLE_RATE);
        } catch (Exception exception) {
            log.error("Transcript audio extraction failed transcriptId={} source={}", transcriptId, sourceVideoPath, exception);
            RecordingTranscriptSupport.deleteIfExists(log, outputAudioPath, "audio");
            throw exception;
        }
    }
}
