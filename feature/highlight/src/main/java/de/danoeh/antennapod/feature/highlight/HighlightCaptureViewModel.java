package de.danoeh.antennapod.feature.highlight;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.PodcastHighlight;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.feed.TranscriptType;
import de.danoeh.antennapod.parser.transcript.TranscriptParser;
import de.danoeh.antennapod.storage.database.DBWriter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HighlightCaptureViewModel extends AndroidViewModel {
    public enum State { PRE_STEP, TRANSCRIBING, EDITING }

    private final MutableLiveData<State> stateLiveData = new MutableLiveData<>(State.PRE_STEP);
    private final MutableLiveData<String> transcriptLiveData = new MutableLiveData<>("");
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> savedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> syncErrorCodeLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> retryAfterSecLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isStreamingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> modelNotDownloadedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> roamSavedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> roamSyncErrorLiveData = new MutableLiveData<>();

    private FeedItem feedItem;
    private int positionMs;
    private int lookbackSec;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled = false;
    private final WhisperTranscriptionService whisperService = new WhisperTranscriptionService();
    private PodcastHighlight savedHighlight = null;

    public HighlightCaptureViewModel(@NonNull Application application) {
        super(application);
        lookbackSec = WhisperPreferences.getLookbackSec();
    }

    public LiveData<State> getState() { return stateLiveData; }
    public LiveData<String> getTranscript() { return transcriptLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<Boolean> getSaved() { return savedLiveData; }
    public LiveData<Integer> getSyncErrorCode() { return syncErrorCodeLiveData; }
    public LiveData<Integer> getRetryAfterSec() { return retryAfterSecLiveData; }
    public LiveData<Boolean> getIsStreaming() { return isStreamingLiveData; }
    public LiveData<Boolean> getModelNotDownloaded() { return modelNotDownloadedLiveData; }
    public LiveData<Boolean> getRoamSaved() { return roamSavedLiveData; }
    public LiveData<Integer> getRoamSyncErrorCode() { return roamSyncErrorLiveData; }
    public int getLookbackSec() { return lookbackSec; }

    public void init(FeedItem item, int currentPositionMs) {
        this.feedItem = item;
        this.positionMs = currentPositionMs;
    }

    public void setLookbackSec(int sec) {
        this.lookbackSec = sec;
    }

    public void startTranscription() {
        stateLiveData.setValue(State.TRANSCRIBING);
        cancelled = false;
        executor.execute(() -> {
            String text = getTranscriptText();
            if (!cancelled) {
                transcriptLiveData.postValue(text);
                stateLiveData.postValue(State.EDITING);
            }
        });
    }

    public void cancelTranscription() {
        cancelled = true;
        whisperService.cancel();
    }

    public void saveHighlight(String text, String note) {
        if (feedItem == null) {
            return;
        }
        executor.execute(() -> {
            PodcastHighlight highlight = ensureSaved(text, note);
            syncToReadwise(highlight);
        });
    }

    public void saveToRoam(String text, String note) {
        if (feedItem == null) {
            return;
        }
        executor.execute(() -> {
            PodcastHighlight highlight = ensureSaved(text, note);
            syncToRoam(highlight);
        });
    }

    private PodcastHighlight ensureSaved(String text, String note) {
        if (savedHighlight != null) {
            return savedHighlight;
        }
        FeedMedia media = feedItem.getMedia();
        String podcastName = feedItem.getFeed() != null ? feedItem.getFeed().getTitle() : null;
        String episodeTitle = feedItem.getTitle();
        String episodeUrl = media != null ? media.getDownloadUrl() : null;
        String imageLocation = feedItem.getImageLocation();
        String coverImageUrl = (imageLocation != null && imageLocation.startsWith("http"))
                ? imageLocation : null;
        int positionSec = positionMs / 1000;
        long capturedAt = System.currentTimeMillis();

        PodcastHighlight highlight = new PodcastHighlight(
                feedItem.getId(), podcastName, episodeTitle, episodeUrl,
                coverImageUrl, text, note, positionSec, capturedAt);
        try {
            DBWriter.savePodcastHighlight(highlight).get();
        } catch (Exception ignored) {}
        savedHighlight = highlight;
        return highlight;
    }

    private void syncToRoam(PodcastHighlight highlight) {
        if (!RoamPreferences.isConfigured()) {
            return;
        }
        RoamSyncService syncService = new RoamSyncService(
                RoamPreferences.getApiToken(), RoamPreferences.getGraphName());
        syncService.syncHighlight(highlight, new RoamSyncService.Callback() {
            @Override
            public void onSuccess() {
                DBWriter.updatePodcastHighlightRoamSyncStatus(highlight.getId(), PodcastHighlight.SYNC_SYNCED);
                roamSavedLiveData.postValue(true);
            }

            @Override
            public void onError(int httpCode, String message) {
                DBWriter.updatePodcastHighlightRoamSyncStatus(highlight.getId(), PodcastHighlight.SYNC_ERROR);
                roamSyncErrorLiveData.postValue(httpCode);
            }

            @Override
            public void onNetworkError(Exception e) {
                DBWriter.updatePodcastHighlightRoamSyncStatus(highlight.getId(), PodcastHighlight.SYNC_ERROR);
                errorLiveData.postValue(e.getMessage());
            }
        });
    }

    private void syncToReadwise(PodcastHighlight highlight) {
        String token = ReadwisePreferences.getApiToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        ReadwiseSyncService syncService = new ReadwiseSyncService(token);
        syncService.syncHighlight(highlight, new ReadwiseSyncService.Callback() {
            @Override
            public void onSuccess() {
                DBWriter.updatePodcastHighlightSyncStatus(highlight.getId(), PodcastHighlight.SYNC_SYNCED);
                savedLiveData.postValue(true);
            }

            @Override
            public void onError(int httpCode, String message, int retryAfterSec) {
                DBWriter.updatePodcastHighlightSyncStatus(highlight.getId(), PodcastHighlight.SYNC_ERROR);
                syncErrorCodeLiveData.postValue(httpCode);
                if (retryAfterSec > 0) {
                    retryAfterSecLiveData.postValue(retryAfterSec);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                DBWriter.updatePodcastHighlightSyncStatus(highlight.getId(), PodcastHighlight.SYNC_ERROR);
                errorLiveData.postValue(e.getMessage());
                scheduleReadwiseSyncWorker();
            }
        });
    }

    private String getTranscriptText() {
        if (feedItem == null) {
            return "";
        }
        FeedMedia media = feedItem.getMedia();
        if (media != null) {
            String transcriptType = feedItem.getTranscriptType();
            TranscriptType type = TranscriptType.fromMime(transcriptType);
            if (type == TranscriptType.JSON || type == TranscriptType.SRT || type == TranscriptType.VTT) {
                Transcript transcript = loadTranscript(media, transcriptType);
                if (transcript != null) {
                    return extractFromTranscript(transcript);
                }
            }
        }
        if (media != null && media.localFileAvailable()) {
            return transcribeFromFile(media.getLocalFileUrl());
        }
        if (media != null && !media.localFileAvailable()) {
            isStreamingLiveData.postValue(true);
        }
        return "";
    }

    private Transcript loadTranscript(FeedMedia media, String transcriptType) {
        if (media.getItem() != null && media.getItem().getTranscript() != null) {
            return media.getItem().getTranscript();
        }
        String url = feedItem.getTranscriptUrl();
        if (url == null) {
            return null;
        }
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                return TranscriptParser.parse(body, transcriptType);
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private String extractFromTranscript(Transcript transcript) {
        long endMs = positionMs;
        long startMs = endMs - (long) lookbackSec * 1000;
        StringBuilder sb = new StringBuilder();
        int count = transcript.getSegmentCount();
        for (int i = 0; i < count; i++) {
            TranscriptSegment segment = transcript.getSegmentAt(i);
            if (segment.getStartTime() >= startMs && segment.getStartTime() < endMs) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(segment.getWords());
            }
        }
        return sb.toString();
    }

    private String transcribeFromFile(String localFileUrl) {
        File cacheDir = getApplication().getCacheDir();
        File wavFile = new File(cacheDir, "capture_tmp.wav");
        AudioCaptureHelper helper = new AudioCaptureHelper();
        boolean ok = helper.extractAudioSegment(localFileUrl, positionMs, lookbackSec * 1000, wavFile);
        if (!ok) {
            return "";
        }
        String model = WhisperPreferences.getWhisperModel();
        if (!ModelDownloadManager.isModelDownloaded(getApplication(), model)) {
            modelNotDownloadedLiveData.postValue(true);
            return "";
        }
        File modelDir = ModelDownloadManager.getModelDir(getApplication(), model);
        String result = whisperService.transcribe(wavFile.getAbsolutePath(), modelDir.getAbsolutePath());
        wavFile.delete();
        return result;
    }

    public void scheduleReadwiseSyncWorker() {
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();
        androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(ReadwiseSyncWorker.class)
                .setConstraints(constraints)
                .build();
        androidx.work.WorkManager.getInstance(getApplication())
                .enqueueUniqueWork("readwise_retry", androidx.work.ExistingWorkPolicy.KEEP, request);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
