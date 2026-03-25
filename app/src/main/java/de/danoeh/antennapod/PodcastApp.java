package de.danoeh.antennapod;

import android.app.Application;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import de.danoeh.antennapod.feature.highlight.ReadwisePreferences;
import de.danoeh.antennapod.feature.highlight.ReadwiseSyncWorker;
import de.danoeh.antennapod.feature.highlight.RoamPreferences;
import de.danoeh.antennapod.feature.highlight.WhisperPreferences;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

/** Main application class. */
public class PodcastApp extends Application {
    private static final String TAG = "PodcastApp";

    @Override
    public void onCreate() {
        super.onCreate();
        ReadwisePreferences.init(this);
        RoamPreferences.init(this);
        WhisperPreferences.init(this);
        WorkManager.getInstance(this).enqueueUniqueWork(
                "readwise_retry",
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(ReadwiseSyncWorker.class)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .build());
        Thread.setDefaultUncaughtExceptionHandler(new CrashReportExceptionHandler());
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler();

        try {
            // Robolectric calls onCreate for every test, which causes problems with static members
            EventBus.builder()
                    .addIndex(new ApEventBusIndex())
                    .logNoSubscriberMessages(false)
                    .sendNoSubscriberEvent(false)
                    .installDefaultEventBus();
        } catch (EventBusException e) {
            Log.d(TAG, e.getMessage());
        }

        DynamicColors.applyToActivitiesIfAvailable(this);
        ClientConfigurator.initialize(this);
        PreferenceUpgrader.checkUpgrades(this);
    }
}
