package de.danoeh.antennapod.feature.highlight;

import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WhisperTranscriptionService {
    private static final String TAG = "WhisperTranscriptionSvc";
    private static final int SAMPLE_RATE = 16000;

    private volatile boolean cancelled = false;

    // Cached recognizer — reloaded only when modelDir changes
    private OfflineRecognizer recognizer;
    private String loadedModelDir;

    public void cancel() {
        cancelled = true;
    }

    public synchronized String transcribe(String wavFilePath, String modelDirPath) {
        cancelled = false;

        if (!new File(wavFilePath).exists() || !new File(modelDirPath).isDirectory()) {
            return "";
        }

        if (recognizer == null || !modelDirPath.equals(loadedModelDir)) {
            if (recognizer != null) {
                recognizer.release();
                recognizer = null;
            }
            recognizer = buildRecognizer(modelDirPath);
            if (recognizer == null) {
                return "";
            }
            loadedModelDir = modelDirPath;
        }

        float[] samples = readWavSamples(wavFilePath);
        if (samples == null || samples.length == 0 || cancelled) {
            return "";
        }

        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE);
            recognizer.decode(stream);
            String text = recognizer.getResult(stream).getText();
            return text != null ? collapseRepetitions(text.trim()) : "";
        } finally {
            stream.release();
        }
    }

    /**
     * Collapses consecutive repeated word-phrases that Moonshine sometimes hallucinates.
     * E.g. "the the the cat sat" → "the cat sat"
     *      "thank you thank you thank you" → "thank you"
     */
    static String collapseRepetitions(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] words = text.split("\\s+");
        int n = words.length;
        boolean[] skip = new boolean[n];

        // Check each phrase length from longest to shortest so multi-word repetitions
        // are caught before their individual words get processed.
        for (int pLen = 6; pLen >= 1; pLen--) {
            for (int i = 0; i <= n - pLen; i++) {
                if (skip[i]) {
                    continue;
                }
                // Walk forward collapsing any immediate repetitions of the phrase at i.
                int j = i + pLen;
                while (j + pLen <= n) {
                    boolean match = true;
                    for (int k = 0; k < pLen; k++) {
                        if (skip[j + k] || !words[i + k].equalsIgnoreCase(words[j + k])) {
                            match = false;
                            break;
                        }
                    }
                    if (!match) {
                        break;
                    }
                    for (int k = 0; k < pLen; k++) {
                        skip[j + k] = true;
                    }
                    j += pLen;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (!skip[i]) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(words[i]);
            }
        }
        return sb.toString();
    }

    private OfflineRecognizer buildRecognizer(String modelDirPath) {
        try {
            OfflineMoonshineModelConfig moonshine = new OfflineMoonshineModelConfig();
            moonshine.setPreprocessor(modelDirPath + "/preprocess.onnx");
            moonshine.setEncoder(modelDirPath + "/encode.int8.onnx");
            moonshine.setUncachedDecoder(modelDirPath + "/uncached_decode.int8.onnx");
            moonshine.setCachedDecoder(modelDirPath + "/cached_decode.int8.onnx");

            OfflineModelConfig modelConfig = new OfflineModelConfig();
            modelConfig.setMoonshine(moonshine);
            modelConfig.setTokens(modelDirPath + "/tokens.txt");
            modelConfig.setNumThreads(4);

            OfflineRecognizerConfig config = new OfflineRecognizerConfig();
            config.setModelConfig(modelConfig);

            // Pass null for AssetManager — paths are absolute filesystem paths, not assets
            return new OfflineRecognizer(null, config);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build Sherpa-ONNX recognizer", e);
            return null;
        }
    }

    /** Reads a 16-bit PCM WAV file and returns normalised float32 samples. */
    private float[] readWavSamples(String path) {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            // Skip 44-byte standard WAV header
            raf.skipBytes(44);
            long remaining = raf.length() - 44;
            if (remaining <= 0) {
                return null;
            }
            int sampleCount = (int) (remaining / 2);
            byte[] raw = new byte[sampleCount * 2];
            raf.readFully(raw);
            ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            float[] samples = new float[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = buf.getShort() / 32768.0f;
            }
            return samples;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read WAV: " + path, e);
            return null;
        }
    }
}
