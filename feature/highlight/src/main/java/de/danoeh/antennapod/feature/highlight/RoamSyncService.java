package de.danoeh.antennapod.feature.highlight;

import android.util.Log;

import de.danoeh.antennapod.model.feed.PodcastHighlight;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RoamSyncService {
    private static final String TAG = "RoamSyncService";
    private static final String BASE_URL = "https://append-api.roamresearch.com/api/graph/";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiToken;
    private final String graphName;

    public interface Callback {
        void onSuccess();
        void onError(int httpCode, String message);
        void onNetworkError(Exception e);
    }

    public RoamSyncService(String apiToken, String graphName) {
        this.apiToken = apiToken;
        this.graphName = graphName;
        this.client = new OkHttpClient();
    }

    public void syncHighlight(PodcastHighlight highlight, Callback callback) {
        try {
            String podcastName = nullToEmpty(highlight.getPodcastName());
            if (!RoamPreferences.isPageInitialized(podcastName)) {
                int initCode = post(buildPageInitBody(highlight));
                if (initCode != 200) {
                    callback.onError(initCode, "Page init failed");
                    return;
                }
                RoamPreferences.markPageInitialized(podcastName);
            }

            int quoteCode = post(buildQuoteBody(highlight));
            if (quoteCode == 200) {
                callback.onSuccess();
            } else {
                callback.onError(quoteCode, "Quote append failed");
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Roam sync error", e);
            callback.onNetworkError(e);
        }
    }

    private int post(JSONObject body) throws IOException {
        RequestBody requestBody = RequestBody.create(body.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(BASE_URL + graphName + "/append-blocks")
                .addHeader("X-Authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();
        Response response = client.newCall(request).execute();
        int code = response.code();
        if (code != 200) {
            String bodyStr = response.body() != null ? response.body().string() : "";
            Log.e(TAG, "Roam request failed: HTTP " + code + " — " + bodyStr);
        }
        return code;
    }

    private JSONObject buildPageInitBody(PodcastHighlight highlight) throws JSONException {
        String podcastName = nullToEmpty(highlight.getPodcastName());
        String pageTitle = "Podcast/" + podcastName;

        JSONObject page = new JSONObject();
        page.put("title", pageTitle);

        JSONObject location = new JSONObject();
        location.put("page", page);

        JSONArray appendData = new JSONArray();

        JSONObject fullTitleBlock = new JSONObject();
        fullTitleBlock.put("string", "Full Title:: " + podcastName);
        appendData.put(fullTitleBlock);

        if (highlight.getCoverImageUrl() != null && !highlight.getCoverImageUrl().isEmpty()) {
            JSONObject coverChild = new JSONObject();
            coverChild.put("string", "![](" + highlight.getCoverImageUrl() + ")");
            JSONArray coverChildren = new JSONArray();
            coverChildren.put(coverChild);
            JSONObject coverBlock = new JSONObject();
            coverBlock.put("string", "Cover::");
            coverBlock.put("children", coverChildren);
            appendData.put(coverBlock);
        }

        JSONObject tagsBlock = new JSONObject();
        tagsBlock.put("string", "Tags:: #podcast");
        appendData.put(tagsBlock);

        JSONObject ratingBlock = new JSONObject();
        ratingBlock.put("string", "Rating:: #not_populated");
        appendData.put(ratingBlock);

        JSONObject separatorBlock = new JSONObject();
        separatorBlock.put("string", "---");
        appendData.put(separatorBlock);

        JSONObject quotesBlock = new JSONObject();
        quotesBlock.put("string", "quotes::");
        appendData.put(quotesBlock);

        JSONObject body = new JSONObject();
        body.put("location", location);
        body.put("append-data", appendData);
        return body;
    }

    private JSONObject buildQuoteBody(PodcastHighlight highlight) throws JSONException {
        String pageTitle = "Podcast/" + nullToEmpty(highlight.getPodcastName());

        JSONObject page = new JSONObject();
        page.put("title", pageTitle);

        JSONObject nestUnder = new JSONObject();
        nestUnder.put("string", "quotes::");

        JSONObject location = new JSONObject();
        location.put("page", page);
        location.put("nest-under", nestUnder);

        JSONArray children = new JSONArray();

        JSONObject episodeBlock = new JSONObject();
        episodeBlock.put("string", "Episode:: " + nullToEmpty(highlight.getEpisodeTitle()));
        children.put(episodeBlock);

        JSONObject timestampBlock = new JSONObject();
        timestampBlock.put("string", "Timestamp:: Time " + formatTimestamp(highlight.getPositionSec()));
        children.put(timestampBlock);

        JSONObject dateBlock = new JSONObject();
        dateBlock.put("string", "Date Captured:: [[" + roamDate(highlight.getCapturedAt()) + "]]");
        children.put(dateBlock);

        if (highlight.getNote() != null && !highlight.getNote().isEmpty()) {
            JSONObject noteBlock = new JSONObject();
            noteBlock.put("string", "Note:: " + highlight.getNote());
            children.put(noteBlock);
        }

        JSONObject captureBlock = new JSONObject();
        captureBlock.put("string", highlight.getText());
        captureBlock.put("children", children);

        JSONArray appendData = new JSONArray();
        appendData.put(captureBlock);

        JSONObject body = new JSONObject();
        body.put("location", location);
        body.put("append-data", appendData);
        return body;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String formatTimestamp(int totalSec) {
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
    }

    private static String roamDate(long epochMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMs);
        String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int year = cal.get(Calendar.YEAR);
        return month + " " + day + ordinal(day) + ", " + year;
    }

    private static String ordinal(int n) {
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }
}
