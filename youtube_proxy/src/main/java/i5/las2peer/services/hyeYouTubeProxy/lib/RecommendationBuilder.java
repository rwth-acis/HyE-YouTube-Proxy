package i5.las2peer.services.hyeYouTubeProxy.lib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Set;

public abstract class RecommendationBuilder {

    private final static String TITLE_KEY = "title";
    private final static String LINK_KEY = "href";
    private final static String SOURCE_KEY = "src";

    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());

    // Helper function extracting view data from messy description text
    private static String getViews(String metaBlock) {
        String[] parts = metaBlock.split("•");
        if (parts.length < 2) {
            log.severe("Meta block missing '•'-character");
            return null;
        }
        parts = parts[1].split(" ");
        String views;

        if (parts[0].equals(" ")) {
            views = parts[1] + " " + parts[2];
        }
        else {
            views = parts[0] + " " + parts[1];
        }

        return views;
    }

    // Helper function extracting information about upload date from messy description text
    private static String getUploaded(String metaBlock) {
        String[] parts = metaBlock.split("•");
        if (parts.length < 2) {
            log.severe("Meta block missing '•'-character");
            return null;
        }
        parts = parts[1].split(" ");
        String uploaded;

        if (parts[0].equals(" ")) {
            uploaded = parts[3] + " " + parts[4] + " " + parts[5];
        }
        else {
            uploaded = parts[2] + " " + parts[3] + " " + parts[4];
        }

        return uploaded;
    }

    // Helper function extracting video relevant data from messy text
    private static HashMap getVideoDetails(String rawDescription) {
        HashMap<String, String> result = new HashMap<String, String>();

        short phase = 0;
        String buffer = "";
        for (int i = 0; i < rawDescription.length(); ++i) {
            switch (phase) {
                case 0:
                    if (!Util.isBlankSpace(rawDescription.charAt(i)))
                    {
                        buffer += rawDescription.charAt(i);
                        ++phase;
                    }
                    continue;
                case 1:
                    if (rawDescription.charAt(i) == '\n') {
                        result.put("title", buffer);
                        buffer = "";
                        ++phase;
                    }
                    else
                        buffer += rawDescription.charAt(i);
                    continue;
                case 2:
                    if (!Util.isBlankSpace(rawDescription.charAt(i)))
                    {
                        buffer += rawDescription.charAt(i);
                        ++phase;
                    }
                    continue;
                case 3:
                    if (rawDescription.charAt(i) == '\n') {
                        result.put("channel", buffer);
                        buffer = "";
                        ++phase;
                    }
                    else
                        buffer += rawDescription.charAt(i);
                    continue;
                case 4:
                    if (rawDescription.charAt(i) == '•')
                        ++phase;
                    continue;
                case 5:
                    if (Util.isAlphaNumeric(rawDescription.charAt(i)))
                    {
                        buffer += rawDescription.charAt(i);
                        ++phase;
                    }
                    continue;
                case 6:
                    if (rawDescription.charAt(i) == '\n') {
                        result.put("views", buffer);
                        buffer = "";
                        ++phase;
                    }
                    else
                        buffer += rawDescription.charAt(i);
                    continue;
                case 7:
                    if (Util.isAlphaNumeric(rawDescription.charAt(i)))
                    {
                        buffer += rawDescription.charAt(i);
                        ++phase;
                    }
                    continue;
                case 8:
                    if (rawDescription.charAt(i) == '\n')
                    {
                        // remove trailing spaces
                        for (int j = buffer.length()-1; j > 0; --j)
                        {
                            if (Util.isAlphaNumeric(rawDescription.charAt(j)))
                            {
                                buffer = buffer.substring(0, j+1);
                                ++phase;
                                result.put("uploaded", buffer);
                                buffer = "";
                                break;
                            }
                        }
                    }
                    else
                        buffer += rawDescription.charAt(i);
                    break;
                default:
                    break;
            }
        }

        return result;
    }

    // Helper method getting recommendation data from recommendation displayed on main page
    public static Recommendation build(Elements links, Elements imgs, Elements metaBlock) {
        Recommendation rec;
        if (metaBlock.size() < 1) {
            log.info("Not enough meta elements to build Recommendation (" + metaBlock.size() + "/1)");
            return null;
        }
        if (imgs.size() < 2) {
            log.info("Not enough image elements to build Recommendation (" + imgs.size() + "/2)");
            return null;
        }
        if (links.size() < 4) {
            log.info("Not enough link elements to build Recommendation (" + links.size() + "/4)");
            return null;
        }
        try {
            rec = new Recommendation(
                    links.get(1).attr(TITLE_KEY),
                    links.get(2).text(),
                    links.get(0).attr(LINK_KEY),
                    links.get(2).attr(LINK_KEY));
            rec.setThumbnail(imgs.get(0).attr(SOURCE_KEY));
            rec.setAvatar(imgs.get(1).attr(SOURCE_KEY));
            if (links.size() >= 5)
                rec.setDescription(links.get(5).text());
            rec.setViews(getViews(metaBlock.get(0).text()));
            rec.setUploaded(getUploaded(metaBlock.get(0).text()));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }

        return rec;
    }

    // Helper method getting recommendation data from recommendation displayed on video page
    public static Recommendation build(Elements links, Elements imgs) {
        Recommendation rec;
        if (imgs.size() < 2) {
            log.info("Not enough image elements to build Recommendation (" + imgs.size() + "/2)");
            return null;
        }
        if (links.size() < 4) {
            log.info("Not enough link elements to build recommendation (" + links.size() + "/4)");
            return null;
        }
        try {
            HashMap videoDetails = getVideoDetails(links.get(1).text());
            if (!videoDetails.containsKey("title") || !videoDetails.containsKey("channel") ||
                    !videoDetails.containsKey("views") || !videoDetails.containsKey("uploaded")) {
                log.info("Missing information to build recommendation");
                return null;
            }

            rec = new Recommendation(
                    videoDetails.get("title").toString(),
                    videoDetails.get("channel").toString(),
                    links.get(1).attr(LINK_KEY),
                    "",
                    imgs.get(0).attr(SOURCE_KEY),
                    "",
                    videoDetails.get("views").toString(),
                    videoDetails.get("uploaded").toString(),
                    ""
            );
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }

        return rec;
    }

    // Helper method getting recommendation data from JavaScript code sent in response to requesting main page
    public static Recommendation build(JsonObject obj) {
        Recommendation rec = null;

        // There are three different types of recommendation objects
        if (obj.has("richItemRenderer")) {
            try {
                // Actually interesting data is buried a little deeper
                obj = obj.get("richItemRenderer").getAsJsonObject().get("content").getAsJsonObject()
                    .get("videoRenderer").getAsJsonObject();
                System.out.println(obj.toString());

                // Try to create object
                rec = new Recommendation(
                        obj.get("title").getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject()
                                .get("text").getAsString(),
                        obj.get("ownerText").getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject()
                                .get("text").getAsString(),
                        obj.get("navigationEndpoint").getAsJsonObject().get("commandMetadata").getAsJsonObject()
                                .get("webCommandMetadata").getAsJsonObject().get("url").getAsString(),
                        obj.get("ownerText").getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject()
                                .get("navigationEndpoint").getAsJsonObject().get("browseEndpoint").getAsJsonObject()
                                .get("canonicalBaseUrl").getAsString(),
                        obj.get("thumbnail").getAsJsonObject().get("thumbnails").getAsJsonArray().get(0)
                                .getAsJsonObject().get("url").getAsString(),
                        obj.get("channelThumbnailSupportedRenderers").getAsJsonObject()
                                .get("channelThumbnailWithLinkRenderer").getAsJsonObject().get("thumbnail")
                                .getAsJsonObject().get("thumbnails").getAsJsonArray().get(0).getAsJsonObject()
                                .get("url").getAsString(),
                        obj.get("viewCountText").getAsJsonObject().get("simpleText").getAsString(),
                        obj.get("publishedTimeText").getAsJsonObject().get("simpleText").getAsString(),
                        obj.get("descriptionSnippet").getAsJsonObject().get("runs").getAsJsonArray().get(0)
                                .getAsJsonObject().get("text").getAsString()
                );
            } catch (Exception e) {
                log.printStackTrace(e);
            }
        }
        else if (obj.has("compactVideoRenderer")) {
            try {
                obj = obj.get("compactVideoRenderer").getAsJsonObject();
                System.out.println(obj.toString());

                // Try to create object
                rec = new Recommendation(
                        obj.get("title").getAsJsonObject().get("simpleText").getAsString(),
                        obj.get("shortBylineText").getAsJsonObject().get("runs").getAsJsonArray().get(0)
                                .getAsJsonObject().get("text").getAsString(),
                        obj.get("navigationEndpoint").getAsJsonObject().get("commandMetadata").getAsJsonObject()
                                .get("webCommandMetadata").getAsJsonObject().get("url").getAsString(),
                        obj.get("shortBylineText").getAsJsonObject().get("runs").getAsJsonArray().get(0)
                                .getAsJsonObject().get("navigationEndpoint").getAsJsonObject().get("browseEndpoint")
                                .getAsJsonObject().get("canonicalBaseUrl").getAsString(),
                        obj.get("thumbnail").getAsJsonObject().get("thumbnails").getAsJsonArray().get(0)
                                .getAsJsonObject().get("url").getAsString(),
                        obj.get("channelThumbnail").getAsJsonObject().get("thumbnails").getAsJsonArray().get(0)
                                .getAsJsonObject().get("url").getAsString(),
                        obj.get("viewCountText").getAsJsonObject().get("simpleText").getAsString(),
                        obj.get("publishedTimeText").getAsJsonObject().get("simpleText").getAsString(),
                        ""
                );
            } catch (Exception e) {
                log.printStackTrace(e);
            }
        }
        else if (obj.has("videoRenderer")) {
            try {
                obj = obj.get("videoRenderer").getAsJsonObject();

                // Try to create object
                rec = new Recommendation(
                        obj.get("title").getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject()
                                .get("text").getAsString(),
                        obj.get("ownerText").getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject()
                                .get("text").getAsString(),
                        obj.get("navigationEndpoint").getAsJsonObject().get("commandMetadata").getAsJsonObject()
                                .get("webCommandMetadata").getAsJsonObject().get("url").getAsString(),
                        obj.get("ownerText").getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject()
                                .get("navigationEndpoint").getAsJsonObject().get("browseEndpoint").getAsJsonObject()
                                .get("canonicalBaseUrl").getAsString(),
                        obj.get("thumbnail").getAsJsonObject().get("thumbnails").getAsJsonArray().get(0).
                                getAsJsonObject().get("url").getAsString(),
                        obj.get("channelThumbnailSupportedRenderers").getAsJsonObject()
                                .get("channelThumbnailWithLinkRenderer").getAsJsonObject().get("thumbnail")
                                .getAsJsonObject().get("thumbnails").getAsJsonArray().get(0).getAsJsonObject()
                                .get("url").getAsString(),
                        obj.get("viewCountText").getAsJsonObject().get("simpleText").getAsString(),
                        obj.get("publishedTimeText").getAsJsonObject().get("simpleText").getAsString(),
                        obj.get("detailedMetadataSnippets").getAsJsonArray().get(0).getAsJsonObject().get("snippetText")
                                .getAsJsonObject().get("runs").getAsJsonArray().get(0).getAsJsonObject().get("text")
                                .getAsString()
                );
            } catch (Exception e) {
                log.printStackTrace(e);
            }
        }

        return rec;
    }
}
