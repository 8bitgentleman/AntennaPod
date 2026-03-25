package de.danoeh.antennapod.feature.highlight;

import android.content.Context;
import android.content.SharedPreferences;

public abstract class ReadwisePreferences {
    private static final String PREF_NAME = "readwise";
    private static final String PREF_API_TOKEN = "de.danoeh.antennapod.feature.highlight.readwise_token";

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getApiToken() {
        return prefs.getString(PREF_API_TOKEN, null);
    }

    public static void setApiToken(String token) {
        prefs.edit().putString(PREF_API_TOKEN, token).apply();
    }

    public static boolean hasApiToken() {
        return getApiToken() != null && !getApiToken().isEmpty();
    }
}
