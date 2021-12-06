package i5.las2peer.services.hyeYouTubeProxy.lib;

import i5.las2peer.execution.ExecutionContext;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.security.UserAgent;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.microsoft.playwright.options.Cookie;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class IdentityManager {

    private String cookieFile;
    private String headerFile;
    private ArrayList<Cookie> cookies;
    private HashMap<String, String> headers;
    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());

    private final String COOKIE_SUFFIX = "_cookies";
    private final String HEADER_SUFFIX = "_headers";
    private final String YOUTUBE_COOKIE_DOMAIN = ".youtube.com";
    private final String YOUTUBE_COOKIE_PATH = "/";

    // Constructor setting up storage
        public IdentityManager(String cookieFile, String headerFile) {
        this.cookieFile = cookieFile;
        this.headerFile = headerFile;

        if (cookieFile == null || headerFile == null) {
            this.cookies = new ArrayList<Cookie>();
            this.headers = new HashMap<String, String>();
        } else {
            // Load file content to memory
            this.cookies = parseCookiesFromFile(cookieFile);
            this.headers = parseHeadersFromFile(headerFile);
        }
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
                        .setDomain(cookieObj.get("domain").getAsString()).setPath(YOUTUBE_COOKIE_PATH));
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
     * Retrieve a user's YouTube ID
     *
     * @param user The User Agent whose YouTube ID we are interested in
     * @return The YouTube ID linked to the given user
     */
    private String getUserId(UserAgent user) {
        return user.getIdentifier();
    }

    /**
     * Retrieve the handle used to store cookies for the given user
     *
     * @param user The User Agent whose YouTube cookies are getting stored/fetched
     * @return The identifier used to store/fetch the given user's cookies
     */
    private String getCookieHandle(UserAgent user) {
        return getUserId(user) + COOKIE_SUFFIX;
    }

    /**
     * Retrieve the handle used to store headers for the given user
     *
     * @param user The User Agent whose HTTP headers are getting stored/fetched
     * @return The identifier used to store/fetch the given user's headers
     */
    private String getHeaderHandle(UserAgent user) {
        return getUserId(user) + HEADER_SUFFIX;
    }

    /**
     * Checks the validity of the cookies stored in the given Json array
     *
     * @param cookies A Json array of containing cookies data
     * @return A Json array where invalid cookies were removed
     */
    private JsonArray parseCookiesFromJsonArray(JsonArray cookies) {
        JsonArray result = new JsonArray();
        Iterator<JsonElement> it = cookies.iterator();
        while (it.hasNext()) {
            JsonObject newCookie = new JsonObject();
            try {
                JsonObject cookie = it.next().getAsJsonObject();
                newCookie.addProperty("name", cookie.get("name").getAsString());
                newCookie.addProperty("value", cookie.get("value").getAsString());

                // Set domain and path to youtube.com/
                newCookie.addProperty("domain", YOUTUBE_COOKIE_DOMAIN);
                newCookie.addProperty("path", YOUTUBE_COOKIE_PATH);
            } catch (Exception e) {
                log.printStackTrace(e);
                continue;
            }
            result.add(newCookie);
       }
        return result;
    }

    /**
     * Interprets the given String as a Json array and tries to create cookies based on the data
     *
     * @param cookieArray A Json array of containing cookies data
     * @return A Json array where invalid cookies were removed
     */
    private ArrayList<Cookie> JsonStringToCookieArray(String cookieArray) {
        ArrayList<Cookie> result = new ArrayList<Cookie>();
        try {
            JsonArray cookieJsonArray = Util.toJsonArray(cookieArray);
            Iterator<JsonElement> it = cookieJsonArray.iterator();
            while (it.hasNext()) {
                JsonObject cookieObj = (JsonObject) it.next();

                // Skip incomplete cookie objects
                if (
                        !cookieObj.has("name") ||
                        !cookieObj.has("value") ||
                        !cookieObj.has("domain") ||
                        !cookieObj.has("path"))
                    continue;

                result.add(new Cookie(
                        cookieObj.get("name").getAsString(),
                        cookieObj.get("value").getAsString())
                        .setDomain(cookieObj.get("domain").getAsString())
                        .setPath(cookieObj.get("path").getAsString()));
            }
        } catch (Exception e) {
            return null;
        }
        return result;
    }

    /**
     * Retrieve cookies for the given user
     *
     * @param context The current execution context required to access the user's local storage
     * @return Valid YouTube cookies stored for that user
     */
    public ArrayList<Cookie> getCookies(ExecutionContext context) {
        // If cookies were parsed from a static file, return this
        if (!this.cookies.isEmpty())
            return this.cookies;

        // Else retrieve cookies from local storage
        try {
            UserAgent user = (UserAgent) context.getMainAgent();
            Envelope cookieEnvelope = context.requestEnvelope(getCookieHandle(user));
            return JsonStringToCookieArray(cookieEnvelope.getContent().toString());
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieve HTTP headers for the given user
     *
     * @param context The current execution context required to access the user's local storage
     * @return Valid HTTP headers stored for that user
     */
    public HashMap<String, String> getHeaders(ExecutionContext context) {
        // If headers were parsed from a static file, return this
        if (!this.headers.isEmpty())
            return this.headers;

        // Else retrieve headers from local storage
        try {
            UserAgent user = (UserAgent) context.getMainAgent();
            Envelope headerEnvelope = context.requestEnvelope(getHeaderHandle(user));
            return Util.jsonToMap(Util.toJsonObject(headerEnvelope.getContent().toString()));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Store the given cookies for the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param cookies Google cookies
     * @return Stored content as string or error message
     */
    public JsonObject storeCookies(ExecutionContext context, JsonArray cookies) {
        UserAgent user;
        JsonObject response = new JsonObject();
        try {
            user = (UserAgent) context.getMainAgent();
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        JsonArray parsedCookies = parseCookiesFromJsonArray(cookies);
        // TODO add test determining cookie validity (e.g., make request to YouTube and check whether user is logged in)

        String responseMsg = "{'";
        try {
            String cookieHandle = getCookieHandle(user);
            Envelope cookieEnvelope = context.createEnvelope(cookieHandle);
            responseMsg += cookieHandle + "': ";
            String cookieData = parsedCookies.toString();
            cookieEnvelope.setContent(cookieData);
            responseMsg += cookieData + "}";
            // TODO implement shared access
            //cookieEnvelope.addReader();
            context.storeEnvelope(cookieEnvelope);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error storing cookies.");
            return response;
        }
        log.info("Cookies updated for user " + getUserId(user));
        response.addProperty("status", 200);
        response.addProperty("msg", responseMsg);
        return response;
    }

    /**
     * Store the given headers for the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param headers HTTP headers
     */
    public JsonObject storeHeaders(ExecutionContext context, JsonObject headers) {
        UserAgent user;
        JsonObject response = new JsonObject();
        try {
            user = (UserAgent) context.getMainAgent();
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        String responseMsg = "{'";
        try {
            String headerHandle = getHeaderHandle(user);
            Envelope headerEnvelope = context.createEnvelope(headerHandle);
            responseMsg += headerHandle + "': ";
            String headerData = headers.toString();
            headerEnvelope.setContent(headerData);
            responseMsg += headerData + "}";
            // TODO implement shared access
            //cookieEnvelope.addReader();
            context.storeEnvelope(headerEnvelope);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error storing headers.");
            return response;
        }
        log.info("Headers updated for user " + getUserId(user));
        response.addProperty("status", 200);
        response.addProperty("msg", responseMsg);
        return response;
    }
}
