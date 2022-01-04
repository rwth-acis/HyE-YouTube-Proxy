package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

import i5.las2peer.api.Context;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.ServiceAgent;
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
import java.io.Serializable;
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
    private final String CONSENT_SUFFIX = "_consent";
    private final String PERMISSION_TABLE_SUFFIX = "_permissions";
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
     * Helper function which either fetches or creates an envelope with the given handle
     *
     * @param context The current execution context required to access the user's local storage
     * @param handle The handle associated with the envelope in question
     * @return The requested las2peer Envelope
     */
    private Envelope getEnvelope(Context context, String handle, Agent owner) {
        Envelope env;

        // See whether envelope already exists
        try {
            if (owner == null)
                env = context.requestEnvelope(handle);
            else
                env = context.requestEnvelope(handle, owner);
        } catch (Exception e) {
            // Envelope does not exist
            env = null;
        }

        // Else create envelope
        if (env == null) {
            try {
                if (owner == null)
                    env = context.createEnvelope(handle);
                else
                    env = context.createEnvelope(handle, owner);
            } catch (Exception e) {
                log.printStackTrace(e);
                return null;
            }
        }

        return env;
    }

    /**
     * Helper function which either fetches or creates an envelope with the given handle and given content
     *
     * @param context The current execution context required to access the user's local storage
     * @param handle The handle associated with the envelope in question
     * @param content The content to be stored
     * @param owner If not null, the envelope is signed with the given owners private key
     * @return Whether envelope was successfully stored
     */
    private boolean storeEnvelope(Context context, String handle, Serializable content, Agent owner) {
        Envelope env;
        env = getEnvelope(context, handle, owner);

        // Store content
        try {
            env.setContent(content);
            if (owner == null)
                context.storeEnvelope(env);
            else
                context.storeEnvelope(env, owner);
        } catch (Exception e) {
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
     * Retrieve the handle used to store cookies for the given user
     *
     * @param userId The User ID of the agent whose YouTube cookies are getting stored/fetched
     * @return The identifier used to store/fetch the given user's cookies
     */
    private String getCookieHandle(String userId) {
        return userId + COOKIE_SUFFIX;
    }
    private String getCookieHandle(UserAgent user) {
        return getCookieHandle(getUserId(user));
    }

    /**
     * Retrieve the handle used to store headers for the given user
     *
     * @param userId The User ID of the agent whose HTTP headers are getting stored/fetched
     * @return The identifier used to store/fetch the given user's headers
     */
    private String getHeaderHandle(String userId) {
        return userId + HEADER_SUFFIX;
    }
    private String getHeaderHandle(UserAgent user) {
        return getHeaderHandle(getUserId(user));
    }

    /**
     * Retrieve the handle used to store consent objects for the given user
     *
     * @param userId The User ID of the agent whose consent data is getting stored/fetched
     * @return The identifier used to store/fetch the given user's consent data
     */
    private String getConsentHandle(String userId) {
        return userId + CONSENT_SUFFIX;
    }
    private String getConsentHandle(UserAgent user) {
        return getHeaderHandle(getUserId(user));
    }

    /**
     * Retrieve the handle used to store permissions
     *
     * @param serviceAgent The service agent managing the permission table
     * @return The identifier used to store/fetch the given user's headers
     */
    private String getPermissionsHandle(ServiceAgent serviceAgent) {
        return serviceAgent.getIdentifier() + PERMISSION_TABLE_SUFFIX;
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
     * Retrieve a user's YouTube ID
     *
     * @param user The User Agent whose YouTube ID we are interested in
     * @return The YouTube ID linked to the given user
     */
    public String getUserId(UserAgent user) {
        if (user == null)
            return "";
        return user.getIdentifier();
    }

    /**
     * Fetches the user associated with the given handle
     *
     * @param context The current context from which the agent is fetched
     * @param handle The current las2peer ID of the user in question
     * @return The requested las2peer user agent or null if there was an issue
     */
    public UserAgent getUserAgent(Context context, String handle) {
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
     * Initialize smart contracts
     *
     * @param context Current execution context from which the method is called
     * @param consentRegistryAddress The blockchain address where the consent registry contracts are stored
     * @return Whether initialization was successful
     */
    public boolean initialize(ExecutionContext context, String consentRegistryAddress) {
        try {
            ServiceAgentImpl agent = (ServiceAgentImpl) context.getServiceAgent();
            registryClient = ((EthereumNode) agent.getRunningAtNode()).getRegistryClient();
            consentRegistry = registryClient.loadSmartContract(ConsentRegistry.class, consentRegistryAddress);

            // Create empty table for permissions
            if (!storeEnvelope(context, getPermissionsHandle(context.getServiceAgent()),
                    new HashMap<String, HashSet<String>>(), context.getServiceAgent())) {
                log.warning("Initialization failed!");
                return false;
            }
        } catch (Exception e) {
            log.warning("Initialization failed!");
            log.printStackTrace(e);
            return false;
        }
        return true;
    }

    /**
     * Retrieve cookies for the given user
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param ownerId The las2peer ID of the user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @oaram anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid YouTube cookies stored for the requested user if requesting user has required permissions
     */
        public ArrayList<Cookie> getCookies(ExecutionContext context, String ownerId, String reqUri, boolean anon) {
        // If cookies were parsed from a static file, return this
        if (this.cookies != null && !this.cookies.isEmpty()) {
            log.info("Using cookies from file");
            return this.cookies;
        }

        // Else retrieve cookies from las2peer storage
        try {
            String userId = getUserId((UserAgent) context.getMainAgent());
            // Check whether user is allowed to access the owner's cookies
            Envelope cookieEnvelope = context.requestEnvelope(getCookieHandle(ownerId));

            // Make sure that user has the proper permissions to use the cookie for the specified request
            if (ownerId.equals(userId) || checkConsent(new Consent(ownerId, userId, reqUri, anon)))
                return JsonStringToCookieArray(cookieEnvelope.getContent().toString());

            return new ArrayList<Cookie>();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Store the given cookies for the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param cookies YouTube cookies
     * @return Stored content as Json object or error message
     */
    public JsonObject storeCookies(ExecutionContext context, JsonArray cookies) {
        String ownerId;
        JsonObject response = new JsonObject();
        try {
            ownerId = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        JsonArray parsedCookies = parseCookiesFromJsonArray(cookies);
        // TODO add test determining cookie validity (e.g., make request to YouTube and check whether user is logged in)

        String responseMsg = "{'";
        try {
            String cookieHandle = getCookieHandle(ownerId);

            responseMsg += cookieHandle + "': ";
            String cookieData = parsedCookies.toString();
            responseMsg += cookieData + "}";

            if (!storeEnvelope(context, cookieHandle, cookieData, null))
            {
                response.addProperty("status", 500);
                response.addProperty("msg", "Error storing cookies.");
                return response;
            }
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error storing cookies.");
            return response;
        }
        log.info("Cookies updated for user " + ownerId);
        response.addProperty("status", 200);
        response.addProperty("msg", responseMsg);
        return response;
    }

    /**
     * Removes the given cookies of the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @return Appropriate status message
     */
    public JsonObject removeCookies(ExecutionContext context) {
        String ownerId;
        JsonObject response = new JsonObject();
        try {
            ownerId = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        try {
            Envelope cookieEnv = context.requestEnvelope(getCookieHandle(ownerId));
            Envelope headerEnv = context.requestEnvelope(getHeaderHandle(ownerId));
            Envelope permissionsEnv = context.requestEnvelope(getPermissionsHandle(context.getServiceAgent()),
                    context.getServiceAgent());
            HashMap<String, HashSet<String>> permissionMap = (HashMap<String, HashSet<String>>)
                    permissionsEnv.getContent();

            // Remove given user from permission sets (this might be plenty inefficient)
            Iterator<String> mapIt = permissionMap.keySet().iterator();
            while (mapIt.hasNext()) {
                String readerId = mapIt.next();

                // Remove users as readers from cookie and header envelopes
                UserAgent reader = getUserAgent(context, readerId);
                if (cookieEnv.hasReader(reader)) {
                    cookieEnv.revokeReader(reader);
                }
                if (headerEnv.hasReader(reader)) {
                    headerEnv.revokeReader(reader);
                }

                // Remove current user from users' permission list
                HashSet<String> permissions = permissionMap.get(readerId);
                if (permissions != null && permissions.contains(ownerId)) {
                    permissions.remove(ownerId);
                    permissionMap.put(readerId, permissions);
                }
            }

            permissionsEnv.setContent(permissionMap);
            context.storeEnvelope(permissionsEnv, context.getServiceAgent());
            cookieEnv.setContent(null);
            context.storeEnvelope(cookieEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error removing cookies.");
            return response;
        }
        log.info("Cookies removed for user " + ownerId);
        response.addProperty("status", 200);
        response.addProperty("msg", "Data successfully deleted");
        return response;
    }

    /**
     * Retrieve HTTP headers for the given user
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param ownerId The las2peer ID of the user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @oaram anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid HTTP headers stored for that user
     */
    public HashMap<String, String> getHeaders(ExecutionContext context, String ownerId, String reqUri, boolean anon) {
        // If headers were parsed from a static file, return this
        if (this.headers != null && !this.headers.isEmpty()) {
            log.info("Using headers from file");
            return this.headers;
        }

        // Else retrieve headers from local storage
        try {
            String userId = getUserId((UserAgent) context.getMainAgent());
            // Check whether user is allowed to access the owner's headers
            Envelope headerEnvelope = context.requestEnvelope(getHeaderHandle(ownerId));

            // Make sure that user has the proper permissions to use the headers for the specified request
            if (ownerId.equals(userId) || checkConsent(new Consent(ownerId, userId, reqUri, anon)))
                return ParserUtil.jsonToMap(ParserUtil.toJsonObject(headerEnvelope.getContent().toString()));

            return new HashMap<String, String>();
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
     * @return Stored content as Json object or error message
     */
    public JsonObject storeHeaders(ExecutionContext context, JsonObject headers) {
        String userId;
        JsonObject response = new JsonObject();
        try {
            userId = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        String responseMsg = "{'";
        try {
            String headerHandle = getHeaderHandle(userId);

            responseMsg += headerHandle + "': ";
            String headerData = headers.toString();
            responseMsg += headerData + "}";

            if (!storeEnvelope(context, headerHandle, headerData, null)) {
                response.addProperty("status", 500);
                response.addProperty("msg", "Error storing headers.");
                return response;
            }
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error storing headers.");
            return response;
        }
        log.info("Headers updated for user " + userId);
        response.addProperty("status", 200);
        response.addProperty("msg", responseMsg);
        return response;
    }

    /**
     * Function to see which cookies the current user is permitted to access
     *
     * @param context Current execution context (including requesting user)
     * @return Set of user IDs that permitted the current user to access their cookies
     */
    public HashSet<String> getPermissions(ExecutionContext context) {
        try {
            HashMap<String, HashSet<String>> permissionMap = (HashMap<String, HashSet<String>>) context.requestEnvelope(
                    getPermissionsHandle(context.getServiceAgent()), context.getServiceAgent()).getContent();
            return permissionMap.get(getUserId((UserAgent) context.getMainAgent()));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Checks whether the requested consent object is currently stored on the blockchain
     *
     * @param consentObj The specific consent options
     * @return Status code and appropriate message as JSON object
     */
    private boolean checkConsent(Consent consentObj) {
        try {
            byte[] consentHash = Util.soliditySha3(consentObj.toString());
            log.info("Checking for consent " + ParserUtil.bytesToHex(consentHash));
            boolean result = consentRegistry.hashExists(consentHash).sendAsync().get();

            // Consent for non-anonymous requests also entails consent for anonymous ones
            if (!result && !consentObj.getAnon()) {
                consentHash = Util.soliditySha3(consentObj.setAnon(false).toString());
                log.info("Checking for consent " + ParserUtil.bytesToHex(consentHash));
                return consentRegistry.hashExists(consentHash).sendAsync().get();
            }
            return result;
        } catch (Exception e) {
            log.severe("Error while checking consent.");
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Returns all consent objects stored by the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @return Content of given user's consent storage
     */
    public JsonObject getConsent(ExecutionContext context) {
        String userId;
        JsonObject response = new JsonObject();
        try {
            userId = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        try {
            Envelope consentEnv = context.requestEnvelope(getConsentHandle(userId));
            response.add("msg", ParserUtil.toJsonArray(new ArrayList<>((HashSet<String>)
                    consentEnv.getContent())));
            response.addProperty("status", 200);
            return response;
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error getting consent");
            return response;
        }
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

        // Store consent to blockchain
        try {
            byte[] consentHash = Util.soliditySha3(consentObj.toString());
            log.info("Storing consent " + ParserUtil.bytesToHex(consentHash));
            consentRegistry.storeConsent(Util.soliditySha3(consentObj.toString())).sendAsync().get();
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error in blockchain communication.");
            return response;
        }

        // Store consent in las2peer
        try {
            Envelope consentEnv = getEnvelope(context, getConsentHandle(user), null);
            HashSet<String> consents = (HashSet<String>) consentEnv.getContent();
            if (consents == null)
                consents = new HashSet<String>();
            consents.add(consentObj.toString());
            consentEnv.setContent(consents);
            context.storeEnvelope(consentEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error updating consent.");
            return response;
        }

        // Update readers of cookie and header envelopes
        try {
            Envelope cookieEnv = context.requestEnvelope(getCookieHandle(user));
            cookieEnv.addReader(getUserAgent(context, consentObj.getReaderId()));
            context.storeEnvelope(cookieEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Could not grant access to cookies.");
            return response;
        }
        try {
            Envelope headerEnv = context.requestEnvelope(getHeaderHandle(user));
            headerEnv.addReader(getUserAgent(context, consentObj.getReaderId()));
            context.storeEnvelope(headerEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Could not grant access to headers.");
            return response;
        }

        // Update list of cookies, reader may access
        try {
            Envelope permissionsEnv = context.requestEnvelope(getPermissionsHandle(context.getServiceAgent()),
                    context.getServiceAgent());
            HashMap<String, HashSet<String>> permissionMap = (HashMap<String, HashSet<String>>)
                    permissionsEnv.getContent();
            HashSet<String> permissionSet = permissionMap.get(consentObj.getReaderId());
            if (permissionSet == null)
                permissionSet = new HashSet<String>();
            permissionSet.add(consentObj.getOwnerId());
            permissionMap.put(consentObj.getReaderId(), permissionSet);
            context.storeEnvelope(permissionsEnv, context.getServiceAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error updating consent.");
            return response;
        }

        log.info("Consent updated for user " + user);
        response.addProperty("status", 200);
        response.addProperty("msg", consentObj.toString());
        return response;
    }

    /**
     * Invalidates the provided consent object
     *
     * @param context The current execution context required to access the user's local storage
     * @param consentData The consent data given as JSON object
     * @return Status code and appropriate message as JSON object
     */
    public JsonObject revokeConsent(ExecutionContext context, JsonObject consentData) {
        String userId;
        JsonObject response = new JsonObject();
        try {
            userId = getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        Consent consentObj = createConsentObj(userId, consentData);
        if (consentObj == null)
        {
            response.addProperty("status", 400);
            response.addProperty("msg", "Invalid consent data.");
            return response;
        }

        // Revoke consent from blockchain
        try {
            byte[] consentHash = Util.soliditySha3(consentObj.toString());
            log.info("Revoking consent " + ParserUtil.bytesToHex(consentHash));
            consentRegistry.revokeConsent(consentHash).sendAsync().get();
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error in blockchain communication.");
            return response;
        }

        // Revoke consent from las2peer
        try {
            Envelope consentEnv = context.requestEnvelope(getConsentHandle(userId));
            HashSet<String> consents = (HashSet<String>) consentEnv.getContent();
            String consentString = consentObj.toString();
            if (consents != null && consents.contains(consentString))
                consents.remove(consentString);
            consentEnv.setContent(consents);
            context.storeEnvelope(consentEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error updating consent storage.");
            return response;
        }

        log.info("Consent revoked by user " + userId);
        response.addProperty("status", 200);
        response.addProperty("msg", consentObj.toString());
        return response;
    }
}
