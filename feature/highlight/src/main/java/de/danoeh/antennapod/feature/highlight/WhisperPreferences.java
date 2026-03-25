package de.danoeh.antennapod.feature.highlight;

import android.content.Context;
import android.content.SharedPreferences;

public abstract class WhisperPreferences {
    private static final String PREF_NAME = "whisper_transcription";
    private static final String PREF_MODEL = "de.danoeh.antennapod.feature.highlight.whisper_model";
    private static final String PREF_LOOKBACK_SEC = "de.danoeh.antennapod.feature.highlight.lookback_sec";

    public static final String MODEL_TINY = "tiny";
    public static final String MODEL_BASE = "base";

    public static final int DEFAULT_LOOKBACK_SEC = 60;
    public static final int MIN_LOOKBACK_SEC = 15;
    public static final int MAX_LOOKBACK_SEC = 300;

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        ModelDownloadManager.deleteLegacyModels(context);
    }

    public static String getWhisperModel() {
        return prefs.getString(PREF_MODEL, MODEL_BASE);
    }

    public static void setWhisperModel(String model) {
        prefs.edit().putString(PREF_MODEL, model).apply();
    }

    public static int getLookbackSec() {
        return prefs.getInt(PREF_LOOKBACK_SEC, DEFAULT_LOOKBACK_SEC);
    }

    public static void setLookbackSec(int seconds) {
        prefs.edit().putInt(PREF_LOOKBACK_SEC, seconds).apply();
    }
}
