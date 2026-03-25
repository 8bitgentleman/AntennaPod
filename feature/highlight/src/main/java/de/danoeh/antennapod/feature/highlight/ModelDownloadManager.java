package de.danoeh.antennapod.feature.highlight;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelDownloadManager {
    public static final String MODEL_DIR = "moonshine_models";

    // HuggingFace repos for Sherpa-ONNX Moonshine int8 models
    private static final String TINY_BASE_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/";
    private static final String BASE_BASE_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-base-en-int8/resolve/main/";

    private static final String[] MODEL_FILES = {
            "preprocess.onnx",
            "encode.int8.onnx",
            "uncached_decode.int8.onnx",
            "cached_decode.int8.onnx",
            "tokens.txt"
    };

    private static final String KEY_MODEL = "model";
    private static final String KEY_BASE_URL = "base_url";
    private static final String WORK_TAG = "moonshine_model_download";

    /** One-time migration: deletes the old whisper.cpp ggml model directory. */
    public static void deleteLegacyModels(Context context) {
        File legacyDir = new File(context.getFilesDir(), "whisper_models");
        if (legacyDir.isDirectory()) {
            for (File f : legacyDir.listFiles()) {
                f.delete();
            }
            legacyDir.delete();
        }
    }

    public static File getModelDir(Context context, String model) {
        return new File(new File(context.getFilesDir(), MODEL_DIR), model);
    }

    public static boolean isModelDownloaded(Context context, String model) {
        File dir = getModelDir(context, model);
        android.util.Log.d("ModelDownloadManager", "isModelDownloaded: model=" + model + " dir=" + dir.getAbsolutePath() + " isDir=" + dir.isDirectory());
        if (!dir.isDirectory()) {
            return false;
        }
        for (String filename : MODEL_FILES) {
            File f = new File(dir, filename);
            android.util.Log.d("ModelDownloadManager", "  checking " + filename + " exists=" + f.exists());
            if (!f.exists()) {
                return false;
            }
        }
        return true;
    }

    public static void downloadModel(Context context, String model) {
        String baseUrl = WhisperPreferences.MODEL_TINY.equals(model) ? TINY_BASE_URL : BASE_BASE_URL;
        Data inputData = new Data.Builder()
                .putString(KEY_MODEL, model)
                .putString(KEY_BASE_URL, baseUrl)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ModelDownloadWorker.class)
                .addTag(WORK_TAG)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    public static void deleteModel(Context context, String model) {
        File dir = getModelDir(context, model);
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                f.delete();
            }
            dir.delete();
        }
    }

    public static class ModelDownloadWorker extends Worker {
        public ModelDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
            return Futures.immediateFuture(buildForegroundInfo());
        }

        private ForegroundInfo buildForegroundInfo() {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    getApplicationContext(), NotificationUtils.CHANNEL_ID_DOWNLOADING)
                    .setContentTitle(getApplicationContext().getString(
                            R.string.whisper_download_notification_title))
                    .setSmallIcon(de.danoeh.antennapod.ui.notifications.R.drawable.ic_notification_sync)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return new ForegroundInfo(R.id.notification_whisper_download, builder.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            }
            return new ForegroundInfo(R.id.notification_whisper_download, builder.build());
        }

        @NonNull
        @Override
        public Result doWork() {
            String model = getInputData().getString(KEY_MODEL);
            String baseUrl = getInputData().getString(KEY_BASE_URL);
            if (model == null || baseUrl == null) {
                return Result.failure();
            }
            setForegroundAsync(buildForegroundInfo());

            File dir = ModelDownloadManager.getModelDir(getApplicationContext(), model);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            OkHttpClient client = new OkHttpClient();
            for (String filename : MODEL_FILES) {
                File outputFile = new File(dir, filename);
                Request request = new Request.Builder().url(baseUrl + filename).build();
                try {
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful() || response.body() == null) {
                        deletePartialDownload(dir);
                        return Result.failure();
                    }
                    try (InputStream in = response.body().byteStream();
                         FileOutputStream out = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                } catch (IOException e) {
                    deletePartialDownload(dir);
                    return Result.failure();
                }
            }
            return Result.success();
        }

        private void deletePartialDownload(File dir) {
            if (dir.exists()) {
                for (File f : dir.listFiles()) {
                    f.delete();
                }
                dir.delete();
            }
        }
    }
}
