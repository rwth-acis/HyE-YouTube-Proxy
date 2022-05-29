package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

import i5.las2peer.execution.ExecutionContext;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.security.ServiceAgentImpl;
import i5.las2peer.serialization.SerializeTools;
import i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.registry.ReadWriteRegistryClient;
import i5.las2peer.registry.Util;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import i5.las2peer.services.hyeYouTubeProxy.lib.L2pUtil;
import i5.las2peer.services.hyeYouTubeProxy.lib.ParserUtil;
import i5.las2peer.tools.CryptoTools;

import javax.crypto.SecretKey;
import javax.ws.rs.core.Cookie;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.*;

/**
 * IdentityManager.
 *
 * This class is managing the YouTube cookies and access permissions.
 */

public class IdentityManager {

    private String cookieFile;
    private String headerFile;
    private ArrayList<Cookie> cookies;
    private HashMap<String, String> headers;
    private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
    private ReadWriteRegistryClient registryClient;
    private ConsentRegistry consentRegistry;
    private String serviceAgentName;
    private String serviceAgentPw;
    private SecretKey secretKey;
    private KeyPair signKey;

    private final String COOKIE_SUFFIX = "_cookies";
    private final String HEADER_SUFFIX = "_headers";
    private final String CONSENT_SUFFIX = "_consent";
    private final String PERMISSION_TABLE_SUFFIX = "_permissions";
    private final String YOUTUBE_COOKIE_DOMAIN = ".youtube.com";
    private final String YOUTUBE_COOKIE_PATH = "/";

    /**
     * Constructor
     *
     * @param cookieFile Local file path containing static set of cookies (used for debugging)
     * @param headerFile Local file path containing static set of headers (used for debugging)
     */
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
                cookies.add(new Cookie(cookieObj.get("name").getAsString(), cookieObj.get("value").getAsString(),
                        YOUTUBE_COOKIE_PATH, cookieObj.get("domain").getAsString()));
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
        return getCookieHandle(L2pUtil.getUserId(user));
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
        return getHeaderHandle(L2pUtil.getUserId(user));
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
        return getHeaderHandle(L2pUtil.getUserId(user));
    }

    /**
     * Retrieve the handle used to store permissions
     *
     * @param serviceAgentId las2peer Agent ID of service agent managing the permission table
     * @return The identifier used to store/fetch the given user's headers
     */
    private String getPermissionsHandle(String serviceAgentId) { return serviceAgentId + PERMISSION_TABLE_SUFFIX; }

    /**
     * Checks the format of the cookies stored in the given Json array
     *
     * @param cookies A Json array of containing cookies data
     * @return A Json array where invalid cookies were removed
     */
    public JsonArray parseCookiesFromJsonArray(JsonArray cookies) {
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
    public ArrayList<Cookie> JsonStringToCookieArray(String cookieArray) {
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
                        cookieObj.get("value").getAsString(),
                        cookieObj.get("path").getAsString(),
                        cookieObj.get("domain").getAsString()));
                        // Cookies which name start with underscore, have to be secure
                        //.setSecure(cookieObj.get("name").getAsString().startsWith("_")));
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
     * Initialize smart contracts
     *
     * @param context Current execution context from which the method is called
     * @param consentRegistryAddress The blockchain address where the consent registry contracts are stored
     * @param serviceAgentName The user agent name of the las2peer agent created to manage the envelopes
     *	holding persistent service information
     * @param serviceAgentPw The user agent password required to unlock this service agent
     * @return Whether initialization was successful
     */
    public boolean initialize(ExecutionContext context, String consentRegistryAddress, String serviceAgentName,
                              String serviceAgentPw) {
        try {
            UserAgent serviceAgent = (UserAgent) context.fetchAgent(
                    context.getUserAgentIdentifierByLoginName(serviceAgentName));
            registryClient = ((EthereumNode) ((ServiceAgentImpl) context.getServiceAgent()).getRunningAtNode())
                    .getRegistryClient();
            consentRegistry = registryClient.loadSmartContract(ConsentRegistry.class, consentRegistryAddress);

            // Create empty table for permissions
            serviceAgent.unlock(serviceAgentPw);
            if (!L2pUtil.storeEnvelope(context, getPermissionsHandle(L2pUtil.getUserId(serviceAgent)),
                    new HashMap<String, HashSet<String>>(), serviceAgent, log)) {
                log.warning("Initialization failed!");
                return false;
            }
            this.serviceAgentName = serviceAgentName;
            this.serviceAgentPw = serviceAgentPw;
            this.secretKey = CryptoTools.generateSymmetricKey();
            this.signKey = CryptoTools.generateKeyPair();
        } catch (Exception e) {
            log.warning("Initialization failed!");
            log.printStackTrace(e);
            return false;
        }
        return true;
    }

    /**
     * Helper function to retrieve cookies for the given user from las2peer storage
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param ownerId The las2peer ID of the user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @param anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid YouTube cookies stored for the requested user if requesting user has required permissions as String
     */
    private String getCookiesFromStorage(ExecutionContext context, String ownerId, String reqUri, boolean anon) {
        try {
            String userId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
            // Check whether user is allowed to access the owner's cookies
            Envelope cookieEnvelope = context.requestEnvelope(getCookieHandle(ownerId));

            // Make sure that user has the proper permissions to use the cookie for the specified request
            if (ownerId.equals(userId) || checkConsent(context, new Consent(ownerId, userId, reqUri, anon))) {
                // Decrypt cookie (sometimes stuff gets added to encrypted cookies, so use head and tail markings)
                int head = 0, tail = 0, statusH = 0, statusT = 0, count = 0;
                byte[] content = SerializeTools.serialize(SerializeTools.deserializeBase64(cookieEnvelope.getContent()
                        .toString()));
                for (byte b : content) {
                    count++;
                    if ((int) b == -1)
                        statusH++;
                    else
                        statusH = 0;
                    if (head == 0 && statusH >= 5)
                        head = count;
                    if ((int) b == 1)
                        statusT++;
                    else
                        statusT = 0;
                    if (tail == 0 && statusT >= 5)
                        tail = count - statusT;
                }
                byte[] enc = new byte[tail-head];
                count = 0;
                for (int i = head; i < tail; i++)
                    enc[count++] = content[i];
                String dec = new String(CryptoTools.decryptSymmetric(enc, secretKey));
                // Remove padding
                head = 0;
                tail = 0;
                statusH = 0;
                count = 0;
                for (int i = 0; i < dec.length(); i++) {
                    if (statusH < 3 && dec.charAt(i) == '[') {
                        statusH = 1;
                        head = i;
                        continue;
                    }
                    if (statusH == 1) {
                        if (dec.charAt(i) == '{')
                            statusH = 2;
                        else
                            statusH = 0;
                        continue;
                    }
                    if (statusH == 2) {
                        if (dec.charAt(i) == '"') {
                            statusH = 3;
                            count = 1;
                        }
                        else
                            statusH = 0;
                        continue;
                    }
                    if (statusH == 3) {
                        if (dec.charAt(i) == '[')
                            count++;
                        if (dec.charAt(i) == ']')
                            count--;
                        if (count == 0)
                            tail = i;
                    }
                }
                dec = dec.substring(head, tail+1);
                System.out.println(dec);
                return dec;
            }
            return "";
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieve cookies for the given user as Array List
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param ownerId The las2peer ID of the user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @param anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid YouTube cookies stored for the requested user if requesting user has required permissions
     */
    public ArrayList<Cookie> getCookies(ExecutionContext context, String ownerId, String reqUri, boolean anon) {
        // If cookies were parsed from a static file, return this
        if (this.cookies != null && !this.cookies.isEmpty()) {
            log.info("Using cookies from file");
            return this.cookies;
        }

        // Else retrieve cookies from las2peer storage
        String cookieString = getCookiesFromStorage(context, ownerId, reqUri, anon);
        if (cookieString == null)
            return null;
        if (cookieString.length() < 1)
            return new ArrayList<Cookie>();
        return JsonStringToCookieArray(cookieString);
    }

    /**
     * Retrieve cookies for the given user as Json Array
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param ownerId The las2peer ID of the user whose cookies are requested
     * @param reqUri The request URI for which the cookies are requested
     * @param anon Whether the identity of the cookies' owner is known to the requesting user
     * @return Valid YouTube cookies stored for the requested user if requesting user has required permissions
     */
    public JsonArray getCookiesAsJson(ExecutionContext context, String ownerId, String reqUri, boolean anon) {
        String cookieString = getCookiesFromStorage(context, ownerId, reqUri, anon);
        if (cookieString == null)
            return null;
        if (cookieString.length() < 1)
            return new JsonArray();
        return ParserUtil.toJsonArray(cookieString);
    }

    /**
     * Kind of a hacky function, but it's required right now
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param ownerId The las2peer ID of the user whose cookies are requested
     * @return Valid YouTube cookies stored for the requested user if requesting user has required permissions
     */
    private ArrayList<Cookie> getCookies(ExecutionContext context, String ownerId) {
        return getCookies(context, ownerId, YouTubeProxy.ROOT_URI, true);
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
            ownerId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        JsonArray parsedCookies = parseCookiesFromJsonArray(cookies);

        String responseMsg = "{\"";
        try {
            String cookieHandle = getCookieHandle(ownerId);

            responseMsg += cookieHandle + "\": ";
            String cookieData = parsedCookies.toString();
            responseMsg += cookieData + "}";

            // Encrypt with key passed in properties file
            byte[] enc = CryptoTools.encryptSymmetric(cookieData, this.secretKey);
            System.out.println(enc.length);
            // Sometimes stuff gets added to encrypted cookies, so mark head and tail
            byte[] content = new byte[enc.length+10];
            for (int i = 0; i < content.length; i++) {
                if (i < 5)
                    content[i] = (byte) -1;
                else if (content.length - i <= 5)
                    content[i] = (byte) 1;
                else
                    content[i] = enc[i-5];
            }
            if (!L2pUtil.storeEnvelope(context, cookieHandle, SerializeTools.serializeToBase64(content), null, log))
            {
                response.addProperty("status", 500);
                response.addProperty("msg", "Error storing cookies.");
                return response;
            }
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error encrypting cookies.");
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
        HashMap<String, HashSet<String>> permissionMap;
        try {
            ownerId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        try {
            Envelope cookieEnv = context.requestEnvelope(getCookieHandle(ownerId));
//            Envelope permissionsEnv = context.requestEnvelope(getPermissionsHandle(context.getServiceAgent()),
//                    context.getServiceAgent());
//            permissionMap = (HashMap<String, HashSet<String>>)
//                    permissionsEnv.getContent();
//
//            // Remove given user from permission sets (this might be plenty inefficient)
//            Iterator<String> mapIt = permissionMap.keySet().iterator();
//            while (mapIt.hasNext()) {
//                String readerId = mapIt.next();
//
//                // Remove users as readers from cookie envelope
//                UserAgent reader = L2pUtil.getUserAgent(context, readerId, log);
//                if (cookieEnv.hasReader(reader) && !readerId.equals(ownerId)) {
//                    cookieEnv.revokeReader(reader);
//                }
//
//                // Remove current user from users' permission list
//                HashSet<String> permissions = permissionMap.get(readerId);
//                if (permissions != null && permissions.contains(ownerId)) {
//                    permissions.remove(ownerId);
//                    permissionMap.put(readerId, permissions);
//                }
//            }
//
//            permissionsEnv.setContent(permissionMap);
//            context.storeEnvelope(permissionsEnv, context.getServiceAgent());
            cookieEnv.setContent(null);
            context.storeEnvelope(cookieEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error removing cookies.");
            return response;
        }

        // Also delete headers, if some were stored
        try {
            Envelope headerEnv = context.requestEnvelope(getHeaderHandle(ownerId));
//            Iterator<String> mapIt = permissionMap.keySet().iterator();
//            while (mapIt.hasNext()) {
//                String readerId = mapIt.next();
//                UserAgent reader = L2pUtil.getUserAgent(context, readerId, log);
//                if (headerEnv.hasReader(reader) && !readerId.equals(ownerId)) {
//                    headerEnv.revokeReader(reader);
//                }
//            }
            headerEnv.setContent(null);
            context.storeEnvelope(headerEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
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
     * @param anon Whether the identity of the cookies' owner is known to the requesting user
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
            String userId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
            // Check whether user is allowed to access the owner's headers
            Envelope headerEnvelope = context.requestEnvelope(getHeaderHandle(ownerId));

            // Make sure that user has the proper permissions to use the headers for the specified request
            if (ownerId.equals(userId) || checkConsent(context, new Consent(ownerId, userId, reqUri, anon)))
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
            userId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        String responseMsg = "{\"";
        try {
            String headerHandle = getHeaderHandle(userId);

            responseMsg += headerHandle + "\": ";
            String headerData = headers.toString();
            responseMsg += headerData + "}";

            if (!L2pUtil.storeEnvelope(context, headerHandle, headerData, null, log)) {
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
     * Helper function to create a signed hash from the given consent object
     *
     * @param context The current execution context required to fetch the cookies accessible to the current user
     * @param consentObj An object specifying the consent parameters
     * @return The hash computed over the consent object as JSON string signed by the service agent
     */
    // TODO this should probably happen on the blockchain, instead
    private byte[] getConsentHash(ExecutionContext context, Consent consentObj) {
        try {
            return Util.soliditySha3(new String (CryptoTools.signContent(consentObj.toString()
                            .getBytes(StandardCharsets.UTF_8), signKey.getPrivate())));
        } catch(Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Function to see which cookies the current user is permitted to access
     *
     * @param context Current execution context (including requesting user)
     * @return Set of user IDs that permitted the current user to access their cookies
     */
    public HashSet<String> getPermissions(ExecutionContext context) {
        try {
            UserAgent serviceAgent = (UserAgent) context.fetchAgent(
                    context.getUserAgentIdentifierByLoginName(serviceAgentName));
            serviceAgent.unlock(serviceAgentPw);
            HashMap<String, HashSet<String>> permissionMap = (HashMap<String, HashSet<String>>) context.requestEnvelope(
                    getPermissionsHandle(L2pUtil.getUserId(serviceAgent)), serviceAgent).getContent();
            // only return users who have valid cookies stored!
            HashSet<String> result = permissionMap.get(L2pUtil.getUserId((UserAgent) context.getMainAgent()));
            for (String ownerId : result) {
                if (getCookies(context, ownerId) == null)
                    result.remove(ownerId);
            }
            return result;
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
    public boolean checkConsent(ExecutionContext context, Consent consentObj) {
        return true;
        try {
            // Create signed consent object
            byte[] consentHash = getConsentHash(context, consentObj);
            if (consentHash == null)
                return false;
            log.info("Checking for consent " + ParserUtil.bytesToHex(consentHash));
            boolean result = consentRegistry.hashExists(consentHash).sendAsync().get();

            // Consent for non-anonymous requests also entails consent for anonymous ones
            if (!result && consentObj.getAnon()) {
                consentHash = getConsentHash(context, consentObj.setAnon(false));
                if (consentHash == null)
                    return false;
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
            userId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 401);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        try {
            Envelope consentEnv = context.requestEnvelope(getConsentHandle(userId));
            response.add("msg", ParserUtil.toJsonArray((HashSet<String>) consentEnv.getContent()));
            response.addProperty("status", 200);
        } catch (i5.las2peer.api.persistency.EnvelopeNotFoundException e){
            response.addProperty("status", 200);
            response.add("msg", new JsonArray());
            return response;
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error getting consent");
        }
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
        String userId;
        JsonObject response = new JsonObject();
        try {
            userId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
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

        // Store consent to blockchain
        // try {
        //     byte[] consentHash = getConsentHash(context, consentObj);
        //     if (consentHash == null) {
        //         response.addProperty("status", 500);
        //         response.addProperty("msg", "Error creating consent hash");
        //         return response;
        //     }
        //     log.info("Storing consent " + ParserUtil.bytesToHex(consentHash));
        //     consentRegistry.storeConsent(consentHash).sendAsync().get();
        // } catch (Exception e) {
        //     log.printStackTrace(e);
        //     response.addProperty("status", 500);
        //     response.addProperty("msg", "Error in blockchain communication.");
        //     return response;
        // }

        // Store consent in las2peer
        try {
            Envelope consentEnv = L2pUtil.getEnvelope(context, getConsentHandle(userId), null, log);
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

        log.info("Consent updated for user " + userId);
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
            userId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
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

        // Trying to revoke non-existent consent seems to cause issues
        if (checkConsent(context, consentObj)) {
            // Revoke consent from blockchain
            try {
                byte[] consentHash = getConsentHash(context, consentObj);
                if (consentHash == null) {
                    response.addProperty("status", 500);
                    response.addProperty("msg", "Error creating consent hash");
                    return response;
                }
                log.info("Revoking consent " + ParserUtil.bytesToHex(consentHash));
                consentRegistry.revokeConsent(consentHash).sendAsync().get();
            } catch (Exception e) {
                log.printStackTrace(e);
                response.addProperty("status", 500);
                response.addProperty("msg", "Error in blockchain communication.");
                return response;
            }
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

    /**
     * Adds the given users as readers of the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param readerIds The IDs of the users to be added
     * @return Status code and appropriate message as JSON object
     */
    public JsonObject addReader(ExecutionContext context, JsonArray readerIds) {
        String ownerId;
        JsonObject response = new JsonObject();
        try {
            ownerId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        String readerId = "";
        try {
            UserAgent serviceAgent = (UserAgent) context.fetchAgent(
                    context.getUserAgentIdentifierByLoginName(serviceAgentName));
            serviceAgent.unlock(serviceAgentPw);
            Envelope cookieEnv = context.requestEnvelope(getCookieHandle(ownerId));
            Envelope permissionsEnv = context.requestEnvelope(getPermissionsHandle(L2pUtil.getUserId(serviceAgent)),
                    serviceAgent);
            HashMap<String, HashSet<String>> permissionMap = (HashMap<String, HashSet<String>>)
                    permissionsEnv.getContent();

            Iterator<JsonElement> it = readerIds.iterator();
            while (it.hasNext()) {
                readerId = it.next().getAsString();
                UserAgent reader = L2pUtil.getUserAgent(context, readerId, log);

                // Update readers of cookie envelope
                cookieEnv.addReader(reader);

                // Update list of cookies, reader may access
                HashSet<String> permissionSet = permissionMap.get(readerId);
                if (permissionSet == null)
                    permissionSet = new HashSet<String>();
                permissionSet.add(ownerId);
                permissionMap.put(readerId, permissionSet);
            }
            context.storeEnvelope(cookieEnv);
            permissionsEnv.setContent(permissionMap);
            context.storeEnvelope(permissionsEnv, serviceAgent);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error adding reader " + readerId);
            return response;
        }

        // Update readers of header envelope, if one exists
        try {
            Envelope headerEnv = context.requestEnvelope(getHeaderHandle(ownerId));
            Iterator<JsonElement> it = readerIds.iterator();
            while (it.hasNext()) {
                headerEnv.addReader(L2pUtil.getUserAgent(context, it.next().getAsString(), log));
            }
            context.storeEnvelope(headerEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
        }
        response.addProperty("status", 200);
        response.addProperty("msg", "Readers successfully added.");
        return response;
    }

    /**
     * Adds the given users as readers of the current user
     *
     * @param context The current execution context required to access the user's local storage
     * @param readerIds The IDs of the users to be added
     * @return Status code and appropriate message as JSON object
     */
    public JsonObject removeReader(ExecutionContext context, JsonArray readerIds) {
        String ownerId;
        JsonObject response = new JsonObject();
        try {
            ownerId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 400);
            response.addProperty("msg", "Could not get user agent. Are you logged in?");
            return response;
        }

        String readerId = "";
        try {
            UserAgent serviceAgent = (UserAgent) context.fetchAgent(
                    context.getUserAgentIdentifierByLoginName(serviceAgentName));
            serviceAgent.unlock(serviceAgentPw);
            Envelope cookieEnv = context.requestEnvelope(getCookieHandle(ownerId));
            Envelope permissionsEnv = context.requestEnvelope(getPermissionsHandle(L2pUtil.getUserId(serviceAgent)),
                    serviceAgent);
            HashMap<String, HashSet<String>> permissionMap = (HashMap<String, HashSet<String>>)
                    permissionsEnv.getContent();

            Iterator<JsonElement> it = readerIds.iterator();
            while (it.hasNext()) {
                readerId = it.next().getAsString();
                if (readerId.equals(ownerId))
                    continue;
                UserAgent reader = L2pUtil.getUserAgent(context, readerId, log);

                // Update readers of cookie envelope
                if (cookieEnv.hasReader(reader) && !readerId.equals(ownerId))
                    cookieEnv.revokeReader(reader);

                // Update list of cookies, reader may access
                HashSet<String> permissionSet = permissionMap.get(readerId);
                if (permissionSet != null && permissionSet.contains(ownerId))
                    permissionSet.remove(ownerId);
                permissionMap.put(readerId, permissionSet);
            }
            context.storeEnvelope(cookieEnv);
            permissionsEnv.setContent(permissionMap);
            context.storeEnvelope(permissionsEnv, serviceAgent);
        } catch (Exception e) {
            log.printStackTrace(e);
            response.addProperty("status", 500);
            response.addProperty("msg", "Error removing reader " + readerId);
            return response;
        }

        // Also update header envelope if one exists
        try {
            Envelope headerEnv = context.requestEnvelope(getHeaderHandle(ownerId));
            Iterator<JsonElement> it = readerIds.iterator();
            while (it.hasNext()) {
                readerId = it.next().getAsString();
                UserAgent reader = L2pUtil.getUserAgent(context, readerId, log);
                if (headerEnv.hasReader(reader) && !readerId.equals(ownerId))
                    headerEnv.revokeReader(L2pUtil.getUserAgent(context, readerId, log));
            }
            context.storeEnvelope(headerEnv);
        } catch (Exception e) {
            log.printStackTrace(e);
        }
        response.addProperty("status", 200);
        response.addProperty("msg", "Readers successfully removed.");
        return response;
    }
}
