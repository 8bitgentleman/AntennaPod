package de.danoeh.antennapod.feature.highlight;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class AudioCaptureHelper {
    private static final String TAG = "AudioCaptureHelper";
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_CHANNEL_COUNT = 1;

    public boolean extractAudioSegment(String mediaFilePath, int endPositionMs, int durationMs, File outputWav) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(mediaFilePath);
            int audioTrackIndex = selectAudioTrack(extractor);
            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found");
                return false;
            }
            extractor.selectTrack(audioTrackIndex);
            int startPositionMs = Math.max(0, endPositionMs - durationMs);
            extractor.seekTo((long) startPositionMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            // SEEK_TO_PREVIOUS_SYNC may land before startPositionMs (keyframes can be 30-60s apart).
            // Capture actual seek position so we can trim the extra leading audio afterward.
            long actualSeekTimeUs = extractor.getSampleTime();

            MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int srcChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            Log.d(TAG, "Source audio: " + mime + " " + srcSampleRate + "Hz " + srcChannelCount + "ch");

            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            byte[] rawPcm = decodeToPcm(extractor, codec, endPositionMs);
            codec.stop();
            codec.release();
            extractor.release();

            if (srcChannelCount > 1) {
                rawPcm = downmixToMono(rawPcm, srcChannelCount);
            }
            if (srcSampleRate != TARGET_SAMPLE_RATE) {
                rawPcm = resample(rawPcm, srcSampleRate, TARGET_SAMPLE_RATE);
            }

            // Trim any leading audio that precedes startPositionMs due to sync-frame overshoot.
            long actualSeekTimeMs = actualSeekTimeUs / 1000;
            long trimMs = startPositionMs - actualSeekTimeMs;
            if (trimMs > 0) {
                int trimBytes = (int) Math.min(trimMs * TARGET_SAMPLE_RATE / 1000 * 2, rawPcm.length);
                rawPcm = Arrays.copyOfRange(rawPcm, trimBytes, rawPcm.length);
                Log.d(TAG, "Trimmed " + trimMs + "ms of leading audio from sync-frame overshoot");
            }

            Log.d(TAG, "Output PCM: " + rawPcm.length + " bytes at 16kHz mono");
            writePcmToWav(rawPcm, outputWav);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Audio extraction failed", e);
            extractor.release();
            return false;
        }
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private byte[] decodeToPcm(MediaExtractor extractor, MediaCodec codec, int endPositionMs) throws IOException {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        long endTimeUs = (long) endPositionMs * 1000;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (!outputDone) {
            if (!inputDone) {
                int inputIndex = codec.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0 || extractor.getSampleTime() > endTimeUs) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }
            int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                if (outputBuffer != null && bufferInfo.size > 0) {
                    byte[] chunk = new byte[bufferInfo.size];
                    outputBuffer.get(chunk);
                    out.write(chunk);
                }
                codec.releaseOutputBuffer(outputIndex, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
            }
        }
        return out.toByteArray();
    }

    private byte[] downmixToMono(byte[] src, int channelCount) {
        int frameCount = src.length / (2 * channelCount);
        byte[] mono = new byte[frameCount * 2];
        ByteBuffer srcBuf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dstBuf = ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frameCount; i++) {
            int sum = 0;
            for (int c = 0; c < channelCount; c++) {
                sum += srcBuf.getShort();
            }
            dstBuf.putShort((short) (sum / channelCount));
        }
        return mono;
    }

    private byte[] resample(byte[] src, int srcRate, int dstRate) {
        int srcSamples = src.length / 2;
        int dstSamples = (int) ((long) srcSamples * dstRate / srcRate);
        byte[] dst = new byte[dstSamples * 2];
        ByteBuffer srcBuf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dstBuf = ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dstSamples; i++) {
            double srcPos = (double) i * srcRate / dstRate;
            int idx = (int) srcPos;
            double frac = srcPos - idx;
            int s0 = srcBuf.getShort(idx * 2);
            int s1 = (idx + 1 < srcSamples) ? srcBuf.getShort((idx + 1) * 2) : s0;
            dstBuf.putShort((short) (s0 + frac * (s1 - s0)));
        }
        return dst;
    }

    private void writePcmToWav(byte[] pcm, File wavFile) throws IOException {
        int sampleRate = TARGET_SAMPLE_RATE;
        int channelCount = TARGET_CHANNEL_COUNT;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channelCount * bitsPerSample / 8;
        int blockAlign = channelCount * bitsPerSample / 8;
        long dataSize = pcm.length;
        long chunkSize = 36 + dataSize;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt((int) chunkSize);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) channelCount);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt((int) dataSize);

        try (FileOutputStream fos = new FileOutputStream(wavFile)) {
            fos.write(header.array());
            fos.write(pcm);
        }
    }
}
