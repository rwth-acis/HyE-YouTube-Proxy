package i5.las2peer.services.hyeYouTubeProxy.lib;

/**
 * Recommendation
 *
 * Container for relevant YouTube video recommendation data
 */

public class Recommendation {
    // Video Title
    private String title;
    // Channel name of uploader
    private String channelName;
    // Link to video
    private String link;
    // Link to uploader
    private String channelLink;
    // Link to video thumbnail
    private String thumbnail;
    // Link to profile picture of uploader
    private String avatar;
    // Text describing the amount of views
    private String views;
    // Text describing the upload date
    private String uploaded;
    // Video description text
    private String description;

    // Constructor with necessary information
    public Recommendation(String title, String channelName, String link, String channelLink) {
        this.title = title;
        this.channelName = channelName;
        this.link = link;
        this.channelLink = channelLink;
        this.thumbnail = "";
        this.avatar = "";
        this.views = "";
        this.uploaded = "";
        this.description = "";
    }

    // Constructor with additional information
    public Recommendation(String title, String channelName, String link, String channelLink, String thumbnail,
                          String avatar) {
        this.title = title;
        this.channelName = channelName;
        this.link = link;
        this.channelLink = channelLink;
        this.thumbnail = thumbnail;
        this.avatar = avatar;
        this.views = "";
        this.uploaded = "";
        this.description = "";
    }

    // Constructor with all information
    public Recommendation(String title, String channelName, String link, String channelLink, String thumbnail,
                          String avatar, String views, String uploaded, String description) {
        this.title = title;
        this.channelName = channelName;
        this.link = link;
        this.channelLink = channelLink;
        this.thumbnail = thumbnail;
        this.avatar = avatar;
        this.views = views;
        this.uploaded = uploaded;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getChannelLink() {
        return channelLink;
    }

    public void setChannelLink(String channelLink) {
        this.channelLink = channelLink;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getViews() {
        return views;
    }

    public void setViews(String views) {
        this.views = views;
    }

    public String getUploaded() {
        return uploaded;
    }

    public void setUploaded(String uploaded) {
        this.uploaded = uploaded;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
