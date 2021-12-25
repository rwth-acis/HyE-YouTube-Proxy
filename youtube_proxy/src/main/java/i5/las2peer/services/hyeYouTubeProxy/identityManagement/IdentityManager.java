package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

import i5.las2peer.api.Context;
import i5.las2peer.execution.ExecutionContext;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.security.ServiceAgentImpl;
import i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.registry.ReadWriteRegistryClient;
import i5.las2peer.registry.Util;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.microsoft.playwright.options.Cookie;
import i5.las2peer.services.hyeYouTubeProxy.lib.ParserUtil;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class IdentityManager {

    private String cookieFile;
    private String headerFile;
    private ArrayList<Cookie> cookies;
    private HashMap<String, String> headers;
    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
    private ReadWriteRegistryClient registryClient;
    private ConsentRegistry consentRegistry;

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

    public boolean initialize(ExecutionContext context, String consentRegistryAddress) {
        try {
            ServiceAgentImpl agent = (ServiceAgentImpl) context.getServiceAgent();
            registryClient = ((EthereumNode) agent.getRunningAtNode()).getRegistryClient();
            consentRegistry = registryClient.loadSmartContract(ConsentRegistry.class, consentRegistryAddress);
        } catch (Exception e) {
            log.warning("Initialization of smart contracts failed!");
            log.printStackTrace(e);
            return false;
        }
        return true;
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
     * Fetches the user associated with the given handle
     *
     * @param context The current context from which the agent is fetched
     * @param handle The current las2peer ID of the user in question
     * @return The requested las2peer user agent or null if there was an issue
     */
    private UserAgent getUserAgent(Context context, String handle) {
        UserAgent user;
        try {
            user = (UserAgent) context.fetchAgent(handle);
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        return user;
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
            JsonArray cookieJsonArray = ParserUtil.toJsonArray(cookieArray);
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
     * Helper function which either fetches or creates an envelope with the given handle
     *
     * @param context The current execution context required to access the user's local storage
     * @param handle The handle associated with the envelope in question
     * @return The requested las2peer envelope objet
     */
    private Envelope getEnvelope(Context context, String handle) {
        Envelope env;

        // See whether envelope already exists
        try {
            env = context.requestEnvelope(handle);
            return env;
        } catch (Exception e) {
            // Envelope does not exist
            env = null;
        }

        // Else create envelope
        try {
            env = context.createEnvelope(handle);
        } catch (Exception e) {
            env = null;
        }
        return env;
    }

    /**
     * Helper function building a Consent object from the provided JSON data
     *
     * @param userId The user id of the owner of the consent object in question
     * @param consentData The consent options as JSON data
     * @return A valid Consent object or null, if provided data was flawed
     */
    private Consent createConsentObj(String userId, JsonObject consentData) {
        try {
            return new Consent(userId, consentData.get("reader").getAsString(),
                    consentData.get("requestUri").getAsString(), consentData.get("anonymous").getAsBoolean());
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieve cookies for the given user
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param owner The user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @oaram anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid YouTube cookies stored for the requested user if requesting user has required permissions
     */
    public ArrayList<Cookie> getCookies(ExecutionContext context, UserAgent owner, String reqUri, boolean anon) {
        // If cookies were parsed from a static file, return this
        if (this.cookies != null && !this.cookies.isEmpty()) {
            System.out.println("Using cookies from file");
            return this.cookies;
        }

        // Else retrieve cookies from las2peer storage
        try {
            // Check whether user is allowed to access the owner's cookies
            Envelope cookieEnvelope = context.requestEnvelope(getCookieHandle(owner));

            // Make sure that user has the proper permissions to use the cookie for the specified request
            if (!checkConsent(new Consent(getUserId(owner), getUserId((UserAgent) context.getMainAgent()), reqUri,
                    anon))) {
                System.out.println("Lacking consent");
                return null;
            }
            System.out.println("Got cookies:\n" + cookieEnvelope.getContent().toString());
            return JsonStringToCookieArray(cookieEnvelope.getContent().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Retrieve HTTP headers for the given user
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param owner The user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @oaram anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid HTTP headers stored for that user
     */
    public HashMap<String, String> getHeaders(ExecutionContext context, UserAgent owner, String reqUri, boolean anon) {
        // If headers were parsed from a static file, return this
        if (this.headers != null && !this.headers.isEmpty()) {
            log.info("Using headers from file");
            return this.headers;
        }

        // Else retrieve headers from local storage
        try {
            // Check whether user is allowed to access the owner's headers
            Envelope headerEnvelope = context.requestEnvelope(getHeaderHandle(owner));

            // Make sure that user has the proper permissions to use the headers for the specified request
            if (!checkConsent(new Consent(getUserId(owner), getUserId((UserAgent) context.getMainAgent()), reqUri,
                    anon))) {
                log.info("Lacking consent");
                return null;
            }
            return ParserUtil.jsonToMap(ParserUtil.toJsonObject(headerEnvelope.getContent().toString()));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Store the given headers for the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param headers HTTP headers
     * @param readers Identifiers of las2peer users, allowed to access stored headers
     * @return Stored content as Json object or error message
     */
    public JsonObject storeHeaders(ExecutionContext context, JsonObject headers, ArrayList<String> readers) {
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
            Envelope headerEnvelope = getEnvelope(context, headerHandle);

            responseMsg += headerHandle + "': ";
            String headerData = headers.toString();
            headerEnvelope.setContent(headerData);
            responseMsg += headerData + "}";

            Iterator<String> it = readers.iterator();
            while (it.hasNext()) {
                UserAgent reader = getUserAgent(context, it.next());
                if (reader != null)
                    headerEnvelope.addReader(reader);
            }

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

    /**
     * Store the given cookies for the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param cookies YouTube cookies
     * @param readers Identifiers of las2peer users, allowed to access stored cookies
     * @return Stored content as Json object or error message
     */
    public JsonObject storeCookies(ExecutionContext context, JsonArray cookies, ArrayList<String> readers) {
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
            Envelope cookieEnvelope = getEnvelope(context, cookieHandle);

            responseMsg += cookieHandle + "': ";
            String cookieData = parsedCookies.toString();
            cookieEnvelope.setContent(cookieData);
            responseMsg += cookieData + "}";

            Iterator<String> it = readers.iterator();
            while (it.hasNext()) {
                UserAgent reader = getUserAgent(context, it.next());
                if (reader != null)
                    cookieEnvelope.addReader(reader);
            }

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
     * Stores the given consent options for the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param consentData The consent data as JSON object
     * @return Status code and appropriate message as JSON object
     */
    public JsonObject updateConsent(ExecutionContext context, JsonObject consentData) {
        String user;
        JsonObject response = new JsonObject();
        try {
            user = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        Consent consentObj = createConsentObj(user, consentData);
        if (consentObj == null)
        {
            response.addProperty("status", 400);
            response.addProperty("msg", "Invalid consent data.");
            return response;
        }

        try {
            consentRegistry.storeConsent(Util.soliditySha3(consentObj.toString())).sendAsync().get();
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error in blockchain communication.");
            return response;
        }

        log.info("Consent updated for user " + user);
        response.addProperty("status", 200);
        response.addProperty("msg", consentObj.toString());
        return response;
    }

    /**
     * Checks whether the requested consent object is currently stored on the blockchain
     *
     * @param consentObj The specific consent options
     * @return Status code and appropriate message as JSON object
     */
    private boolean checkConsent(Consent consentObj) {
        System.out.println("Checking for consent " + consentObj.toString());
        try {
            return consentRegistry.hashExists(Util.soliditySha3(consentObj.toString())).sendAsync().get();
        } catch (Exception e) {
            System.out.println("Error while checking consent.");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Invalidates the provided consent object
     *
     * @param context The current execution context required to access the user's local storage
     * @param consentData The consent data given as JSON object
     * @return Status code and appropriate message as JSON object
     */
    public JsonObject revokeConsent(ExecutionContext context, JsonObject consentData) {
        String user;
        JsonObject response = new JsonObject();
        try {
            user = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        Consent consentObj = createConsentObj(user, consentData);
        if (consentObj == null)
        {
            response.addProperty("status", 400);
            response.addProperty("msg", "Invalid consent data.");
            return response;
        }

        try {
            consentRegistry.revokeConsent(Util.soliditySha3(consentObj.toString())).sendAsync().get();
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error in blockchain communication.");
            return response;
        }

        log.info("Consent revoked by user " + user);
        response.addProperty("status", 200);
        response.addProperty("msg", consentObj.toString());
        return response;
    }
}
