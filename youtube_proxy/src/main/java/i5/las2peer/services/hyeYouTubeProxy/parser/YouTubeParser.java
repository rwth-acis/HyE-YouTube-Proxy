package i5.las2peer.services.hyeYouTubeProxy.parser;

import java.util.ArrayList;
import java.util.Iterator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public abstract class YouTubeParser {

    private final static String METADATA_CLASS = "ytd-video-meta-block";
    private final static String THUMBNAIL_TAG = "ytd-thumbnail";
    private final static String IMAGE_TAG = "img";
    private final static String LINK_TAG = "a";
    private final static String SECONDARY_TAG = "secondary";

    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());

    // Helper function used to find the given object key in the given html code and convert its value into a Json object
    private static JsonObject getMainObject(String html, String mainObjKey) {
        int htmlLength = html.length();

        // Try to find given object key
        int pos = html.indexOf(mainObjKey);
        if (pos < 0 || pos >= htmlLength + mainObjKey.length())
            return null;

        // Go to beginning of Json String
        pos += mainObjKey.length();
        while (html.charAt(pos) != '{')
            ++pos;

        // Write the object into buffer
        char[] buffer = new char[htmlLength - pos];
        int bracketCount = 1;
        int bufferLength = 1;
        buffer[0] = '{';
        while (bracketCount > 0 && pos < htmlLength) {
            ++pos;
            ++bufferLength;
            buffer[bufferLength] = html.charAt(pos);
            if (html.charAt(pos) == '{')
                ++bracketCount;
            else if (html.charAt(pos) == '}') //&& (html.charAt(pos-1) != '\\' || html.charAt(pos-2) == '\\'))
                --bracketCount;
        }

        // Convert buffer to Json
        JsonObject mainObj;
        try {
            mainObj = JsonParser.parseString(new String(buffer)).getAsJsonObject();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        return mainObj;
    }

    // Helper function iterating over given array and trying to convert data into recommendations
    private static ArrayList<Recommendation> parseRecsFromContents(JsonArray contents) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        Iterator<JsonElement> it = contents.iterator();
        while (it.hasNext()) {
            JsonObject recObj = it.next().getAsJsonObject();
            Recommendation rec = RecommendationBuilder.build(recObj);
            if (rec == null)
                log.info("Error creating recommendation object from JSON data");
            else
                recs.add(rec);
        }
        return recs;
    }

    /**
     * YouTube's HTML response mainly consists of JavaScript which loads the content,
     * this function tries to extract the displayed recommendations from this JS code.
     *
     * @param html the HTML of YouTube's main page
     * @return Personalized YouTube recommendations
     */
    private static ArrayList<Recommendation> getRecsFromMainJS(String html) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        final String mainObjKey = "twoColumnBrowseResultsRenderer";

        JsonObject mainObj = getMainObject(html, mainObjKey);
        if (mainObj == null) {
            log.info("Unable to find " + mainObjKey + " in given HTML");
            return recs;
        }

        JsonArray contents;
        try {
            contents = mainObj.get("tabs").getAsJsonArray().get(0).getAsJsonObject().get("tabRenderer")
                    .getAsJsonObject().get("content").getAsJsonObject().get("richGridRenderer").getAsJsonObject()
                    .get("contents").getAsJsonArray();

        } catch (Exception e) {
            log.printStackTrace(e);
            return recs;
        }

        // Get recommendation data from array
        recs = parseRecsFromContents(contents);
        return recs;
    }

    /**
     * YouTube's HTML response mainly consists of JavaScript which loads the content,
     * this function tries to extract the displayed recommendations from this JS code.
     *
     * @param html the HTML of YouTube video page
     * @return Personalized YouTube recommendations
     */
    private static ArrayList<Recommendation> getRecsFromAsideJS(String html) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        final String mainObjKey = "twoColumnWatchNextResults";

        JsonObject mainObj = getMainObject(html, mainObjKey);
        if (mainObj == null) {
            log.info("Unable to find " + mainObjKey + " in given HTML");
            return recs;
        }

        JsonArray contents;
        try {
            contents = mainObj.get("secondaryResults").getAsJsonObject().get("secondaryResults").getAsJsonObject()
                    .get("results").getAsJsonArray();

        } catch (Exception e) {
            log.printStackTrace(e);
            return recs;
        }

        // Get recommendation data from array
        recs = parseRecsFromContents(contents);
        return recs;
    }

    /**
     * YouTube's HTML response mainly consists of JavaScript which loads the content,
     * this function tries to extract the displayed recommendations from this JS code.
     *
     * @param html the HTML of YouTube results page
     * @return Personalized YouTube search results
     */
    private static ArrayList<Recommendation> getRecsFromResultsJS(String html) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        final String mainObjKey = "twoColumnSearchResultsRenderer";

        JsonObject mainObj = getMainObject(html, mainObjKey);
        if (mainObj == null) {
            log.info("Unable to find " + mainObjKey + " in given HTML");
            return recs;
        }

        JsonArray contents;
        try {
            contents = mainObj.get("primaryContents").getAsJsonObject().get("sectionListRenderer").getAsJsonObject()
                    .get("contents").getAsJsonArray().get(0).getAsJsonObject().get("itemSectionRenderer")
                    .getAsJsonObject().get("contents").getAsJsonArray();

        } catch (Exception e) {
            log.printStackTrace(e);
            return recs;
        }

        // Get recommendation data from array
        recs = parseRecsFromContents(contents);
        return recs;
    }

    /**
     * Parses the given HTML and extracts YouTube video recommendations
     *
     * @param html the HTML of YouTube's main page
     * @return Personalized YouTube recommendations
     */
    public static ArrayList<Recommendation> mainPage(String html) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        Elements thumbnails = body.getElementsByTag(THUMBNAIL_TAG);
        Iterator<Element> it = thumbnails.iterator();
        while (it.hasNext()) {
            Element recommendation = it.next().parent();
            Elements imgs = recommendation.getElementsByTag(IMAGE_TAG);
            Elements links = recommendation.getElementsByTag(LINK_TAG);
            Elements metaBlock = recommendation.getElementsByClass(METADATA_CLASS);

            Recommendation rec = RecommendationBuilder.build(imgs, links, metaBlock);
            if (rec == null)
                log.info("Error creating recommendation object from HTML data");
            else
                recs.add(rec);
        }

        // If no recommendations were found, try another way
        if (recs.isEmpty())
            return getRecsFromMainJS(html);
        else
            return recs;
    }

    /**
     * Parses the given HTML and extracts YouTube video recommendations
     *
     * @param html the HTML of the YouTube video page
     * @return Personalized YouTube recommendations
     */
    public static ArrayList<Recommendation> aside(String html) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        Elements thumbnails = body.getElementsByTag(THUMBNAIL_TAG);
        Iterator<Element> it = thumbnails.iterator();
        while (it.hasNext()) {
            Element recommendation = it.next().parent();
            Elements imgs = recommendation.getElementsByTag(IMAGE_TAG);
            Elements links = recommendation.getElementsByTag(LINK_TAG);

            Recommendation rec = RecommendationBuilder.build(imgs, links);
            if (rec == null)
                log.info("Error creating recommendation object from HTML data");
            else
                recs.add(rec);
        }

        // If no recommendations were found, try another way
        if (recs.isEmpty())
            return getRecsFromAsideJS(html);
        else
            return recs;
    }

    /**
     * Parses the given HTML and extracts YouTube video recommendations
     *
     * @param html the HTML of YouTube search results page
     * @return Personalized YouTube search results
     */
    public static ArrayList<Recommendation> resultsPage(String html) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        Elements thumbnails = body.getElementsByTag(THUMBNAIL_TAG);
        Iterator<Element> it = thumbnails.iterator();
        while (it.hasNext()) {
            Element recommendation = it.next().parent();
            Elements imgs = recommendation.getElementsByTag(IMAGE_TAG);
            Elements links = recommendation.getElementsByTag(LINK_TAG);
            Elements metaBlock = recommendation.getElementsByTag(METADATA_CLASS);

            Recommendation rec = RecommendationBuilder.build(imgs, links, metaBlock);
            if (rec == null)
                log.info("Error creating recommendation object from HTML data");
            else
                recs.add(rec);
        }

        // If no recommendations were found, try another way
        if (recs.isEmpty())
            return getRecsFromResultsJS(html);
        else
            return recs;
    }

    public static boolean loggedInLibrary(String html) {
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        Element secondaryDiv = body.getElementById(SECONDARY_TAG);
        System.out.println(secondaryDiv.text());
        System.out.println(secondaryDiv.children().size());
        return secondaryDiv.children().size() > 0;
    }
}
