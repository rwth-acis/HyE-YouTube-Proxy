package i5.las2peer.services.hyeYouTubeProxy.lib;

import i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.security.UserAgent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.options.Cookie;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class IdentityManager {

    private String cookieFile;
    private String headerFile;
    private ArrayList<Cookie> cookies;
    private HashMap<String, String> headers;
    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());

    // Constructor setting up storage
    // TODO implement las2peer FileService support
    public IdentityManager(String cookieFile, String headerFile) {
        this.cookieFile = cookieFile;
        this.headerFile = headerFile;

        // Load file content to memory
        this.cookies = parseCookiesFromFile(cookieFile);
        this.headers = parseHeadersFromFile(headerFile);
    }

    /**
     * Temporary helper function to read static cookies used for testing from properties file
     *
     * @param filePath Path of the file to read from on the local file system
     * @return Some YouTube cookies
     */
    private ArrayList<Cookie> parseCookiesFromFile(String filePath) {
        ArrayList<Cookie> cookies =  new ArrayList<Cookie>();
        JsonArray cookieList;
        try {
            log.info("Reading cookies from file " + filePath);
            cookieList = JsonParser.parseReader(new FileReader(new File(filePath))).getAsJsonArray();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        for (short i = 0; i < cookieList.size(); ++i) {
            try {
                JsonObject cookieObj = cookieList.get(i).getAsJsonObject();
                cookies.add(new Cookie(cookieObj.get("name").getAsString(), cookieObj.get("value").getAsString())
                        .setDomain(cookieObj.get("domain").getAsString()).setPath("/"));
            } catch (Exception e) {
                log.info("Failed to parse cookie object " + i + " from file " + cookieFile + ". Skipping!");
                log.printStackTrace(e);
            }
        }
        return cookies;
    }

    /**
     * Temporary helper function to read static headers used for testing from properties file
     *
     * @param filePath Path of the file to read from on the local file system
     * @return Some HTTP headers
     */
    private HashMap<String, String> parseHeadersFromFile(String filePath) {
        HashMap<String, String> headers = new HashMap<String, String>();
        JsonObject headerList;
        Set<String> headerKeys;
        try {
            log.info("Reading HTTP headers from file " + filePath);
            headerList = JsonParser.parseReader(new FileReader(new File(filePath))).getAsJsonObject();
            headerKeys = headerList.keySet();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        Iterator<String> it = headerKeys.iterator();
        while (it.hasNext()) {
            String key = it.next();
            try {
                headers.put(key, headerList.get(key).getAsString());
            } catch (Exception e) {
                log.printStackTrace(e);
            }
        }
        return headers;
    }

    /**
     * Helper function to retrieve a user's YouTube ID
     *
     * @param user The User Agent whose YouTube ID we are interested in
     * @return The YouTube ID linked to the given user
     */
    private String getUserId(UserAgent user) {
        return user.getLoginName();
    }

    /**
     * Retrieve cookies for the given user
     *
     * @param user A las2peer user agent
     * @return Valid YouTube cookies stored for that user
     */
    public ArrayList<Cookie> getCookies(UserAgent user) {
        // TODO implement auth code storage
        return this.cookies;
    }

    /**
     * Retrieve HTTP headers for the given user
     *
     * @param user A las2peer user agent
     * @return Valid HTTP headers stored for that user
     */
    public HashMap<String, String> getHeaders(UserAgent user) {
        // TODO implement auth code storage
        return this.headers;
    }

    /**
     * Helper function to store the given headers for the given user
     *
     * @param user A las2peer user agent
     * @param headers HTTP headers
     */
    private void storeHeaders(UserAgent user, HashMap<String, String> headers) {
        // TODO implement auth code storage
        log.info("Headers updated for user " + getUserId(user));
    }

    /**
     * Helper function to store the given cookies for the given user
     *
     * @param user A las2peer user agent
     * @param cookies Google cookies
     */
    private void storeCookies(UserAgent user, Cookie[] cookies) {
        // TODO implement auth code storage
        log.info("Cookies updated for user " + getUserId(user));
    }
}
