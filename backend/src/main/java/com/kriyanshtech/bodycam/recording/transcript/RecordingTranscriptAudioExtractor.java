package com.kriyanshtech.bodycam.recording.transcript;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
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
        Path rawPcmPath = Files.createTempFile("bodycam-transcript-audio-" + transcriptId, ".pcm");
        log.info("Starting transcript audio extraction transcriptId={} source={}", transcriptId, sourceVideoPath);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(sourceVideoPath.toFile())) {
            grabber.start();
            if (grabber.getAudioChannels() <= 0) {
                throw new IllegalStateException("Recording does not contain an audio stream for transcript generation");
            }

            int observedSampleRate = 0;
            int observedChannels = 0;
            long pcmByteCount = 0;
            boolean wroteSamples = false;

            try (DataOutputStream outputStream = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(rawPcmPath)))) {
                LinearPcmResampler resampler = null;
                Frame frame;
                while ((frame = grabber.grabSamples()) != null) {
                    if (frame.samples == null || frame.samples.length == 0) {
                        continue;
                    }

                    int sourceSampleRate = frame.sampleRate > 0 ? frame.sampleRate : grabber.getSampleRate();
                    int sourceChannels = frame.audioChannels > 0 ? frame.audioChannels : grabber.getAudioChannels();
                    if (sourceSampleRate <= 0 || sourceChannels <= 0) {
                        continue;
                    }

                    if (resampler == null) {
                        resampler = new LinearPcmResampler(sourceSampleRate, TARGET_SAMPLE_RATE);
                        observedSampleRate = sourceSampleRate;
                        observedChannels = sourceChannels;
                    } else if (sourceSampleRate != observedSampleRate) {
                        throw new IllegalStateException(
                                "Audio sample rate changed during extraction: " + observedSampleRate + " -> " + sourceSampleRate);
                    }

                    float[] monoSamples = toMonoSamples(frame.samples, sourceChannels);
                    if (monoSamples.length == 0) {
                        continue;
                    }

                    short[] pcmSamples = resampler.resample(monoSamples);
                    if (pcmSamples.length == 0) {
                        continue;
                    }

                    writeLittleEndian(outputStream, pcmSamples);
                    pcmByteCount += (long) pcmSamples.length * 2L;
                    wroteSamples = true;
                }

                if (resampler != null) {
                    short[] tailSamples = resampler.finish();
                    if (tailSamples.length > 0) {
                        writeLittleEndian(outputStream, tailSamples);
                        pcmByteCount += (long) tailSamples.length * 2L;
                        wroteSamples = true;
                    }
                }
            }

            if (!wroteSamples) {
                throw new IllegalStateException("Unable to extract audio samples for transcript generation");
            }

            writeWaveFile(rawPcmPath, outputAudioPath, pcmByteCount);
            log.info(
                    "Extracted transcript audio transcriptId={} inputSampleRate={} inputChannels={} outputSampleRate={} outputChannels={}",
                    transcriptId,
                    observedSampleRate,
                    observedChannels,
                    TARGET_SAMPLE_RATE,
                    TARGET_CHANNELS);
            return new ExtractedTranscriptAudio(outputAudioPath, TARGET_SAMPLE_RATE);
        } catch (Exception exception) {
            log.error("Transcript audio extraction failed transcriptId={} source={}", transcriptId, sourceVideoPath, exception);
            RecordingTranscriptSupport.deleteIfExists(log, outputAudioPath, "audio");
            throw exception;
        } finally {
            RecordingTranscriptSupport.deleteIfExists(log, rawPcmPath, "raw-audio");
        }
    }

    private void writeWaveFile(Path rawPcmPath, Path outputAudioPath, long pcmByteCount) throws Exception {
        AudioFormat format = new AudioFormat(TARGET_SAMPLE_RATE, 16, TARGET_CHANNELS, true, false);
        try (AudioInputStream audioInputStream = new AudioInputStream(
                Files.newInputStream(rawPcmPath),
                format,
                pcmByteCount / 2L)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputAudioPath.toFile());
        }
    }

    private void writeLittleEndian(DataOutputStream outputStream, short[] pcmSamples) throws IOException {
        for (short pcmSample : pcmSamples) {
            outputStream.writeByte(pcmSample & 0xFF);
            outputStream.writeByte((pcmSample >>> 8) & 0xFF);
        }
    }

    private float[] toMonoSamples(Buffer[] sampleBuffers, int sourceChannels) {
        if (sampleBuffers.length == 0 || sourceChannels <= 0) {
            return new float[0];
        }

        boolean planar = sampleBuffers.length >= sourceChannels;
        if (planar) {
            int frameCount = sampleBuffers[0].remaining();
            float[] monoSamples = new float[frameCount];
            for (int sampleIndex = 0; sampleIndex < frameCount; sampleIndex++) {
                float mixed = 0.0f;
                for (int channel = 0; channel < sourceChannels; channel++) {
                    mixed += sampleAt(sampleBuffers[channel], sampleIndex);
                }
                monoSamples[sampleIndex] = clamp(mixed / sourceChannels);
            }
            return monoSamples;
        }

        int frameCount = sampleBuffers[0].remaining() / sourceChannels;
        float[] monoSamples = new float[frameCount];
        for (int sampleIndex = 0; sampleIndex < frameCount; sampleIndex++) {
            float mixed = 0.0f;
            int baseIndex = sampleIndex * sourceChannels;
            for (int channel = 0; channel < sourceChannels; channel++) {
                mixed += sampleAt(sampleBuffers[0], baseIndex + channel);
            }
            monoSamples[sampleIndex] = clamp(mixed / sourceChannels);
        }
        return monoSamples;
    }

    private float sampleAt(Buffer buffer, int index) {
        if (buffer instanceof ShortBuffer shortBuffer) {
            return shortBuffer.get(index) / 32768.0f;
        }
        if (buffer instanceof FloatBuffer floatBuffer) {
            return clamp(floatBuffer.get(index));
        }
        if (buffer instanceof DoubleBuffer doubleBuffer) {
            return clamp((float) doubleBuffer.get(index));
        }
        if (buffer instanceof IntBuffer intBuffer) {
            return clamp(intBuffer.get(index) / 2147483648.0f);
        }
        if (buffer instanceof ByteBuffer byteBuffer) {
            return byteBuffer.get(index) / 128.0f;
        }
        throw new IllegalStateException("Unsupported audio sample buffer type: " + buffer.getClass().getName());
    }

    private float clamp(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static final class LinearPcmResampler {
        private final double inputFramesPerOutputFrame;
        private double nextOutputSourceIndex = 0.0d;
        private long totalSourceFrames = 0L;
        private Float previousChunkLastSample;

        private LinearPcmResampler(int sourceSampleRate, int targetSampleRate) {
            this.inputFramesPerOutputFrame = (double) sourceSampleRate / (double) targetSampleRate;
        }

        private short[] resample(float[] sourceSamples) {
            if (sourceSamples.length == 0) {
                return new short[0];
            }

            float[] effectiveSamples;
            long effectiveStartIndex;
            if (previousChunkLastSample != null) {
                effectiveSamples = new float[sourceSamples.length + 1];
                effectiveSamples[0] = previousChunkLastSample;
                System.arraycopy(sourceSamples, 0, effectiveSamples, 1, sourceSamples.length);
                effectiveStartIndex = totalSourceFrames - 1L;
            } else {
                effectiveSamples = sourceSamples;
                effectiveStartIndex = totalSourceFrames;
            }

            long effectiveEndIndex = effectiveStartIndex + effectiveSamples.length - 1L;
            short[] output = new short[Math.max(0, estimateOutputCount(effectiveStartIndex, effectiveEndIndex))];
            int outputCount = 0;

            while (nextOutputSourceIndex < effectiveEndIndex) {
                if (nextOutputSourceIndex < effectiveStartIndex) {
                    nextOutputSourceIndex = effectiveStartIndex;
                }

                double localIndex = nextOutputSourceIndex - effectiveStartIndex;
                int leftIndex = (int) Math.floor(localIndex);
                if (leftIndex < 0 || leftIndex + 1 >= effectiveSamples.length) {
                    break;
                }

                float leftSample = effectiveSamples[leftIndex];
                float rightSample = effectiveSamples[leftIndex + 1];
                float fraction = (float) (localIndex - leftIndex);
                float interpolated = leftSample + ((rightSample - leftSample) * fraction);

                if (outputCount == output.length) {
                    short[] expanded = new short[Math.max(8, output.length * 2)];
                    System.arraycopy(output, 0, expanded, 0, output.length);
                    output = expanded;
                }
                output[outputCount++] = toPcm16(interpolated);
                nextOutputSourceIndex += inputFramesPerOutputFrame;
            }

            previousChunkLastSample = sourceSamples[sourceSamples.length - 1];
            totalSourceFrames += sourceSamples.length;

            short[] exact = new short[outputCount];
            System.arraycopy(output, 0, exact, 0, outputCount);
            return exact;
        }

        private short[] finish() {
            if (previousChunkLastSample == null) {
                return new short[0];
            }

            long lastFrameIndex = totalSourceFrames - 1L;
            if (nextOutputSourceIndex > lastFrameIndex) {
                return new short[0];
            }
            return new short[] { toPcm16(previousChunkLastSample) };
        }

        private int estimateOutputCount(long startIndex, long endIndexExclusiveBase) {
            if (inputFramesPerOutputFrame <= 0.0d) {
                return 0;
            }
            double available = Math.max(0.0d, endIndexExclusiveBase - Math.max(nextOutputSourceIndex, startIndex));
            return (int) Math.ceil(available / inputFramesPerOutputFrame) + 2;
        }

        private short toPcm16(float sample) {
            int value = Math.round(Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
            return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
    }
}
