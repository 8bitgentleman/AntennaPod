package de.danoeh.antennapod.model.feed;

public class PodcastHighlight {
    public static final int SYNC_PENDING = 0;
    public static final int SYNC_SYNCED = 1;
    public static final int SYNC_ERROR = -1;

    private long id;
    private final long feedItemId;
    private final String podcastName;
    private final String episodeTitle;
    private final String episodeUrl;
    private final String coverImageUrl;
    private String text;
    private String note;
    private final int positionSec;
    private final long capturedAt;
    private int syncedReadwise;
    private int syncedRoam;

    public PodcastHighlight(long feedItemId, String podcastName, String episodeTitle,
            String episodeUrl, String coverImageUrl, String text, String note,
            int positionSec, long capturedAt) {
        this.feedItemId = feedItemId;
        this.podcastName = podcastName;
        this.episodeTitle = episodeTitle;
        this.episodeUrl = episodeUrl;
        this.coverImageUrl = coverImageUrl;
        this.text = text;
        this.note = note;
        this.positionSec = positionSec;
        this.capturedAt = capturedAt;
        this.syncedReadwise = SYNC_PENDING;
        this.syncedRoam = SYNC_PENDING;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getFeedItemId() { return feedItemId; }
    public String getPodcastName() { return podcastName; }
    public String getEpisodeTitle() { return episodeTitle; }
    public String getEpisodeUrl() { return episodeUrl; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getPositionSec() { return positionSec; }
    public long getCapturedAt() { return capturedAt; }
    public int getSyncedReadwise() { return syncedReadwise; }
    public void setSyncedReadwise(int syncedReadwise) { this.syncedReadwise = syncedReadwise; }
    public int getSyncedRoam() { return syncedRoam; }
    public void setSyncedRoam(int syncedRoam) { this.syncedRoam = syncedRoam; }
}
