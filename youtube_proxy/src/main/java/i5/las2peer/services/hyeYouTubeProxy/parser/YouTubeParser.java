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

/**
 * YouTubeParser
 *
 * This class parses the HTML responses send in response to the automated requests to YouTube using Playwright in order
 * to obtain personalized recommendations using Jsoup.
 */

public abstract class YouTubeParser {

    private final static String METADATA_CLASS = "ytd-video-meta-block";
    private final static String THUMBNAIL_TAG = "ytd-thumbnail";
    private final static String IMAGE_TAG = "img";
    private final static String LINK_TAG = "a";
    private final static String SECONDARY_TAG = "secondary";

    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());

    /**
     * Helper function used to find the given object key in the given html code and convert its value into a Json object
     *
     * @param html Raw html returned in response to automated browser request
     * @param mainObjKey The string used to identify the beginning of the Json object containing the relevant data
     * @return Relevant video information for all YouTube recommendations displayed on given page as Json object
     */
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
        buffer[0] = '{';
        int bracketCount = 1;
        int bufferLength = 1;
        ++pos;
        while (bracketCount > 0 && pos < htmlLength) {
            char c = html.charAt(pos);
            buffer[bufferLength] = c;
            ++pos;
            ++bufferLength;
            if (c == '{')
                ++bracketCount;
            else if (c == '}') //&& (html.charAt(pos-1) != '\\' || html.charAt(pos-2) == '\\'))
                --bracketCount;
        }

        // Convert buffer to Json
        JsonObject mainObj;
        try {
            mainObj = JsonParser.parseString(new String(buffer).substring(0, bufferLength)).getAsJsonObject();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        return mainObj;
    }

    /**
     * Helper function iterating over given array and trying to convert data into recommendations
     *
     * @param contents Element contained in response to request to YouTube holding video data
     * @return Relevant video information for all YouTube recommendations displayed on given page as array list
     */
    private static ArrayList<Recommendation> parseRecsFromContents(JsonArray contents) {
        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
        Iterator<JsonElement> it = contents.iterator();
        while (it.hasNext()) {
            JsonObject recObj = it.next().getAsJsonObject();
            Recommendation rec = RecommendationBuilder.build(recObj);
            if (rec == null)
                log.warning("Error creating recommendation object from JSON data");
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
            log.severe("Unable to find " + mainObjKey + " in given HTML");
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
            log.severe("Unable to find " + mainObjKey + " in given HTML");
            return recs;
        }

        JsonArray contents;
        try {
            contents = mainObj.get("secondaryResults").getAsJsonObject()
                    .get("secondaryResults").getAsJsonObject().get("results")
                    .getAsJsonArray();
            // Sometimes there is some junk object before the recommendation data
            if (contents.get(0).getAsJsonObject().has("relatedChipCloudRenderer"))
                contents = contents.get(1).getAsJsonObject().get("itemSectionRenderer")
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
            log.severe("Unable to find " + mainObjKey + " in given HTML");
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
          // The proper scraping is unfortunately a bit too inconsistent
//        ArrayList<Recommendation> recs = new ArrayList<Recommendation>();
//        Document doc = Jsoup.parse(html);
//        Element body = doc.body();
//        Elements thumbnails = body.getElementsByTag(THUMBNAIL_TAG);
//        Iterator<Element> it = thumbnails.iterator();
//        while (it.hasNext()) {
//            Element recommendation = it.next().parent();
//            Elements imgs = recommendation.getElementsByTag(IMAGE_TAG);
//            Elements links = recommendation.getElementsByTag(LINK_TAG);
//            Elements metaBlock = recommendation.getElementsByClass(METADATA_CLASS);
//
//            Recommendation rec = RecommendationBuilder.build(imgs, links, metaBlock);
//            if (rec == null)
//                log.warning("Error creating recommendation object from HTML data");
//            else
//                recs.add(rec);
//        }
//
        // If no recommendations were found, try another way
//        if (recs.isEmpty())
            return getRecsFromMainJS(html);
//        else
//            return recs;
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
                log.warning("Error creating recommendation object from HTML data");
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
        // This seems to cause problems sometimes ...
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
                log.warning("Error creating recommendation object from HTML data");
            else
                recs.add(rec);
        }

        // If no recommendations were found, try another way
        if (recs.isEmpty())
            return getRecsFromResultsJS(html);
        else
            return recs;
    }
}
