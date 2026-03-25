package de.danoeh.antennapod.feature.highlight;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import de.danoeh.antennapod.model.feed.PodcastHighlight;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

public class ReadwiseSyncWorker extends Worker {

    public ReadwiseSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String token = ReadwisePreferences.getApiToken();
        if (token == null || token.isEmpty()) {
            return Result.success();
        }
        ReadwiseSyncService service = new ReadwiseSyncService(token);
        List<PodcastHighlight> highlights = DBReader.getPodcastHighlights();
        for (PodcastHighlight h : highlights) {
            if (h.getSyncedReadwise() == PodcastHighlight.SYNC_SYNCED) {
                continue;
            }
            final boolean[] succeeded = {false};
            service.syncHighlight(h, new ReadwiseSyncService.Callback() {
                @Override
                public void onSuccess() {
                    succeeded[0] = true;
                }

                @Override
                public void onError(int httpCode, String message, int retryAfterSec) {
                }

                @Override
                public void onNetworkError(Exception e) {
                }
            });
            if (succeeded[0]) {
                DBWriter.updatePodcastHighlightSyncStatus(h.getId(), PodcastHighlight.SYNC_SYNCED);
            }
        }
        return Result.success();
    }
}
