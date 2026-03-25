package de.danoeh.antennapod.feature.highlight;

import android.util.Log;

import de.danoeh.antennapod.model.feed.PodcastHighlight;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReadwiseSyncService {
    private static final String TAG = "ReadwiseSyncService";
    private static final String BASE_URL = "https://readwise.io/api/v2/";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiToken;

    public interface Callback {
        void onSuccess();
        void onError(int httpCode, String message, int retryAfterSec);
        void onNetworkError(Exception e);
    }

    public interface TokenValidationCallback {
        void onValid();
        void onInvalid();
        void onNetworkError(Exception e);
    }

    public ReadwiseSyncService(String apiToken) {
        this.apiToken = apiToken;
        this.client = new OkHttpClient();
    }

    public void validateToken(TokenValidationCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "auth/")
                .addHeader("Authorization", "Token " + apiToken)
                .get()
                .build();
        try {
            Response response = client.newCall(request).execute();
            if (response.code() == 204) {
                callback.onValid();
            } else {
                callback.onInvalid();
            }
        } catch (IOException e) {
            Log.e(TAG, "Token validation error", e);
            callback.onNetworkError(e);
        }
    }

    public void syncHighlight(PodcastHighlight highlight, Callback callback) {
        try {
            JSONObject highlightObj = buildHighlightJson(highlight);
            JSONArray highlightsArray = new JSONArray();
            highlightsArray.put(highlightObj);
            JSONObject body = new JSONObject();
            body.put("highlights", highlightsArray);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(BASE_URL + "highlights/")
                    .addHeader("Authorization", "Token " + apiToken)
                    .post(requestBody)
                    .build();

            Response response = client.newCall(request).execute();
            int code = response.code();
            if (code == 200) {
                callback.onSuccess();
            } else {
                String bodyStr = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "Sync failed: HTTP " + code + " — " + bodyStr);
                int retryAfterSec = 0;
                if (code == 429) {
                    String retryAfter = response.header("Retry-After");
                    if (retryAfter != null) {
                        try {
                            retryAfterSec = Integer.parseInt(retryAfter.trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                callback.onError(code, bodyStr, retryAfterSec);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Sync highlight error", e);
            callback.onNetworkError(e);
        }
    }

    private JSONObject buildHighlightJson(PodcastHighlight highlight) throws JSONException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String highlightedAt = sdf.format(new Date(highlight.getCapturedAt()));

        JSONObject obj = new JSONObject();
        obj.put("text", highlight.getText());
        if (highlight.getEpisodeTitle() != null && !highlight.getEpisodeTitle().isEmpty()) {
            obj.put("title", highlight.getEpisodeTitle());
        }
        if (highlight.getPodcastName() != null && !highlight.getPodcastName().isEmpty()) {
            obj.put("author", highlight.getPodcastName());
        }
        if (highlight.getCoverImageUrl() != null && !highlight.getCoverImageUrl().isEmpty()) {
            obj.put("image_url", highlight.getCoverImageUrl());
        }
        if (highlight.getEpisodeUrl() != null && !highlight.getEpisodeUrl().isEmpty()) {
            obj.put("source_url", highlight.getEpisodeUrl());
        }
        obj.put("source_type", "AntennaPod");
        obj.put("category", "podcasts");
        if (highlight.getNote() != null && !highlight.getNote().isEmpty()) {
            obj.put("note", highlight.getNote());
        }
        obj.put("location_type", "time_offset");
        obj.put("location", highlight.getPositionSec());
        obj.put("highlighted_at", highlightedAt);
        return obj;
    }
}
