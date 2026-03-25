package de.danoeh.antennapod.feature.highlight;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public abstract class RoamPreferences {
    private static final String PREF_NAME = "roam";
    private static final String PREF_API_TOKEN = "de.danoeh.antennapod.feature.highlight.roam_token";
    private static final String PREF_GRAPH_NAME = "de.danoeh.antennapod.feature.highlight.roam_graph";
    private static final String PREF_INITIALIZED_PAGES = "de.danoeh.antennapod.feature.highlight.roam_initialized_pages";

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

    public static String getGraphName() {
        return prefs.getString(PREF_GRAPH_NAME, null);
    }

    public static void setGraphName(String graphName) {
        prefs.edit().putString(PREF_GRAPH_NAME, graphName).apply();
    }

    public static boolean isConfigured() {
        String token = getApiToken();
        String graph = getGraphName();
        return token != null && !token.isEmpty() && graph != null && !graph.isEmpty();
    }

    public static boolean isPageInitialized(String podcastName) {
        return prefs.getStringSet(PREF_INITIALIZED_PAGES, new HashSet<>()).contains(podcastName);
    }

    public static void markPageInitialized(String podcastName) {
        Set<String> pages = new HashSet<>(prefs.getStringSet(PREF_INITIALIZED_PAGES, new HashSet<>()));
        pages.add(podcastName);
        prefs.edit().putStringSet(PREF_INITIALIZED_PAGES, pages).apply();
    }
}
