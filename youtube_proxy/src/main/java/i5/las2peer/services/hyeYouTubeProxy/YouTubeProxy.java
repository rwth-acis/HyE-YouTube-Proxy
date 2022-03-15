package i5.las2peer.services.hyeYouTubeProxy;

import java.io.Serializable;
import java.net.HttpURLConnection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;

import java.util.*;
import java.util.logging.Level;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import i5.las2peer.api.Context;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.execution.ExecutionContext;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.hyeYouTubeProxy.identityManagement.Consent;
import i5.las2peer.services.hyeYouTubeProxy.identityManagement.IdentityManager;
import i5.las2peer.services.hyeYouTubeProxy.lib.L2pUtil;
import i5.las2peer.services.hyeYouTubeProxy.parser.YouTubeParser;
import i5.las2peer.services.hyeYouTubeProxy.parser.Recommendation;
import i5.las2peer.services.hyeYouTubeProxy.lib.ParserUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.util.Json;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
// import com.microsoft.playwright.options.LoadState;

/**`
 * HyE - YouTube Proxy
 *
 * This service is used to obtain user data from YouTube via scraping.
 * To this end, it stores las2peer identities and associates them with YouTube cookies
 * which are used to obtain YouTube video recommendations.
 */

@Api
@SwaggerDefinition(
		info = @Info(
				title = "YouTube Data Proxy",
				version = "0.2.0",
				description = "Part of How's your Experience. Used to obtain data from YouTube.",
				termsOfService = "http://your-terms-of-service-url.com",
				contact = @Contact(
						name = "Michael Kretschmer",
						email = "kretschmer@dbis.rwth-aachen.de"),
				license = @License(
						name = "your software license name",
						url = "http://your-software-license-url.com")))

@ManualDeployment
@ServicePath("/hye-youtube")
public class YouTubeProxy extends RESTService {

	private final String YOUTUBE_MAIN_PAGE = "https://www.youtube.com/";
	private final String YOUTUBE_VIDEO_PAGE = YOUTUBE_MAIN_PAGE + "watch?v=";
	private final String YOUTUBE_RESULTS_PAGE = YOUTUBE_MAIN_PAGE + "results?search_query=";
	private final String YOUTUBE_PROFILE_PAGE = "https://studio.youtube.com/";
	private final String PREFERENCE_PREFIX = "PREFERENCES_";

	private String debug;
	private String cookieFile;
	private String headerFile;
	private String consentRegistryAddress;
	private String rootUri;
	private String frontendUrls;
	private String serviceAgentName;
	private String serviceAgentPw;

	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
	private static Browser browser = null;
	private static boolean initialized = false;
	private static IdentityManager idm = null;
	private static Random rand = null;
	private ArrayList<String> corsAddresses;

	public static String ROOT_URI;

	/**
	 * Class constructor, initializes member variables
	 */
	public YouTubeProxy() {
		setFieldValues();
		log.info("Got properties: debug = " + debug + ", cookieFile = " + cookieFile + ", headerFile = " +
				headerFile + ", consentRegistryAddress = " + consentRegistryAddress + ", rootUri = " + rootUri +
				", frontendUrls = " + frontendUrls + ", serviceAgentName = " + serviceAgentName +
				", serviceAgentPw = " + serviceAgentPw);

		if (browser == null) {
			Playwright playwright = Playwright.create();
			browser = playwright.chromium().launch();
		}
		if (idm == null) {
			// Do not allow to use static cookies/headers for production
			if (debug != null && debug.equals("true"))
				idm = new IdentityManager(cookieFile, headerFile);
			else
				idm = new IdentityManager(null, null);
		}
		if (rand == null) {
			rand = new Random();
			rand.setSeed(System.currentTimeMillis());
		}
		if (frontendUrls == null) {
			corsAddresses = new ArrayList<String>();
		} else {
			corsAddresses = new ArrayList<String>(Arrays.asList(frontendUrls.split(",")));
		}

		if (ROOT_URI == null && rootUri != null) {
			ROOT_URI = rootUri;
		}

		if (serviceAgentName == null || serviceAgentPw == null)
			log.severe("Cannot initialize service without service agent credentials!");
	}

	private String getVideoUrl(String videoId) {
		return YOUTUBE_VIDEO_PAGE + videoId;
	}

	private String getResultsUrl(String searchQuery) {
		return YOUTUBE_RESULTS_PAGE + searchQuery;
	}

	private String getPreferenceHandle(String userId) { return PREFERENCE_PREFIX + userId; }

	/**
	 * Helper function to find appropriate cookies to use based on existing permissions granted to requesting user.
	 *
	 * @param context The current execution context from which the method is called
	 * @param request The URI of the request for which the cookies (and headers) are used
	 * @return Handle of chosen user
	 */
	private String findMatch(ExecutionContext context, String request) {
		// Get users whose cookies we have access to
		HashSet<String> candidates = idm.getPermissions(context);
		String readerId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
		
		// If there are no other options, just return self
		if (candidates == null || candidates.size() == 0)
			return readerId;

		// If user has preference in list of candidates, use this
		JsonObject resp = getUserPreference(context);
		if (resp.get("status").getAsInt() == 200) {
			for (String userId : candidates) {
				if (resp.get("msg").getAsString().equals(userId)) {
					try {
						// Add one time code to database
						context.invoke(
							"i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations",
							"insertOneTimeCode", new Serializable[] { request, 2.0, 0.0, 0.0 });
					} catch (Exception e) {
						log.printStackTrace(e);
					}
					return resp.get("msg").getAsString();
				}
			}
		}

		String matchedUserId = "";
		try {
			// RMI call with parameters
			matchedUserId = (String) context.invoke(
					"i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations", "findMatch",
					new Serializable[] { candidates, request });
			if (matchedUserId != null)
				return matchedUserId;
		} catch (Exception e) {
			log.printStackTrace(e);
		}

		// If recommendation service isn't running, try to get random user
		// TODO repeat if chosen user did not consent to this particular request
		while (candidates != null && candidates.size() > 0) {
			// Get random candidate
			Iterator<String> it = candidates.iterator();
			int randPos = rand.nextInt(candidates.size());
			while (it.hasNext() && randPos >= 0) {
				matchedUserId = it.next();
				--randPos;
			}
			// Check for permission
			if (idm.checkConsent(new Consent(matchedUserId, readerId, rootUri, true))) {
				break;
			} else {
				// Go again
				candidates.remove(matchedUserId);
				matchedUserId = "";
			}
		}

		// Return current user if even this didn't yield a result
		return matchedUserId.length() > 0 ? matchedUserId : readerId;
	}

	/**
	 * Helper function to set the browser context i.e., getting cookies (and headers) from las2peer storage for request.
	 *
	 * @param l2pContext The current execution context from which the method is called
	 * @param browserContext The browser context to which the cookies (and headers) are added
	 * @param ownerId If the request is made for a particular user, this refers to this user's las2peer ID
	 * @param request The URI of the request for which the cookies (and headers) are used
	 * @return Either an error code and appropriate message or {200: "OK"} to indicate success
	 */
	private JsonObject setContext(ExecutionContext l2pContext, BrowserContext browserContext, String ownerId,
								  String request) {
		JsonObject response = new JsonObject();
		if (!initialized) {
			browserContext.close();
			response.addProperty("500", "Service not initialized!");
			return response;
		}

		// No cookies, no play
		if (!(debug != null && debug.equals("true")) && !hasCookies(l2pContext)) {
			browserContext.close();
			response.addProperty("403",
					"Please upload a valid set of YouTube cookies to use this service!");
			return response;
		}

		// Get cookies (and headers) of appropriate user
		boolean anon = (ownerId == null || ownerId.length() == 0);
		if (anon)
			ownerId = findMatch(l2pContext, request);

		// TODO replace ROOT_URI with actual requested resource
		ArrayList<Cookie> cookies = idm.getCookies(l2pContext, ownerId, ROOT_URI, anon);
		HashMap<String, String> headers = idm.getHeaders(l2pContext, ownerId, ROOT_URI, anon);

		if (cookies == null) {
			browserContext.close();
			response.addProperty("500", "Could not retrieve cookies.");
			return response;
		}
		if (cookies.isEmpty()) {
			browserContext.close();
			response.addProperty("401", "Lacking consent for request.");
			return response;
		}

		try {
				// TODO clean this part up a bit
		        log.info("Setting cookies:");
		        for (Cookie cookie : cookies) {
					log.info(cookie.name + ": " + cookie.value);
					ArrayList<Cookie> singleCookieList = new ArrayList<Cookie>();
					singleCookieList.add(cookie);
					try {
    					    browserContext.addCookies(singleCookieList);
    					} catch (Exception e) {
    					    log.printStackTrace(e);
    					}
				}
			if (headers != null)
				browserContext.setExtraHTTPHeaders(headers);
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Error setting request context.");
			browserContext.close();
			return response;
		}
		response.addProperty("200", "OK");
		return response;
	}

	// Add headers and build response
	private Response buildResponse(int status, Object content) {
		Response.ResponseBuilder responseBuilder = Response.status(status).entity(content);
		Iterator<String> it = corsAddresses.iterator();
		while (it.hasNext()) {
			String url = it.next();
			responseBuilder = responseBuilder.header("Access-Control-Allow-Origin", url);
		}
		return responseBuilder.build();
	}

	/**
	 * Helper function to check whether given user currently has valid cookies stored
	 *
	 * @param context Current las2peer execution context
	 * @return Whether given user currently stores valid YouTube cookies
	 */
	private boolean hasCookies(ExecutionContext context) {
		// Owner should have access regardless off specific resource (request Uri and anonymous)
		ArrayList<Cookie> cookies = idm.getCookies(context, L2pUtil.getUserId((UserAgent) context.getMainAgent()),
				rootUri, true);
		return !(cookies == null || cookies.isEmpty());
	}

	/**
	 * Checks whether the provided JsonArray contains valid cookies by checking response to YouTube profile page
	 *
	 * @param cookies JsonArray of YouTube session cookies
	 * @return Reponse as JsonArray listing status and explanation whether cookie validation succeded
	 */
	private JsonObject validateCookies(JsonArray cookies) {
		JsonObject responseObj = new JsonObject();
		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(YOUTUBE_PROFILE_PAGE));
			// Add cookies to request
			String cookieString = "";
			for (JsonElement cookieElem : cookies)
			{
				JsonObject cookie = cookieElem.getAsJsonObject();
				cookieString += cookie.get("name").getAsString() + '=' + cookie.get("value").getAsString() + "; ";
			}
			requestBuilder.setHeader("Cookie", cookieString);
			HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 400) {
				// 4xx/5xx is bad, but not necessarily the cookies fault
				responseObj.addProperty("status", 500);
				responseObj.addProperty("msg", "Could not validate cookies.");
				return responseObj;
			}
			else if (response.statusCode() >= 300 && response.statusCode() != 304) {
				// 30x means the user is presented with login screen, thus not logged in currently
				responseObj.addProperty("status", 400);
				responseObj.addProperty("msg", "Cookie validation failed.");
				return responseObj;
			}
		} catch (Exception e) {
			log.printStackTrace(e);
			responseObj.addProperty("status", 500);
			responseObj.addProperty("msg", "Could not validate cookies.");
			return responseObj;
		}
		// 200 is what we want, 304 means cached which I assume also means no redirect
		responseObj.addProperty("status", 200);
		responseObj.addProperty("msg", "Cookies are valid.");
		return responseObj;
	}

	/**
	 * Gets the preferred User Agent ID of cookies to use for requests to the given value
	 *
	 * @param context Current las2peer execution context
	 * @return User ID of preferred user, or empty string if no preference is stored
	 */
	private JsonObject getUserPreference(ExecutionContext context) {
		JsonObject response = new JsonObject();
		UserAgent user = null;
		try {
			user = (UserAgent) context.getMainAgent();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("status", 401);
			response.addProperty("msg", "Could not get user agent! Are you logged in?");
			return response;
		}

		// Get envelope
		Envelope env = L2pUtil.getEnvelope(context, getPreferenceHandle(L2pUtil.getUserId(user)), null, log);
		if (env == null) {
			response.addProperty("status", 500);
			response.addProperty("msg", "Could not get preferences.");
			return response;
		}

		if (env.getContent() == null)
		{
			response.addProperty("status", 200);
			response.addProperty("msg", "");
			return response;
		}

		response.addProperty("status", 200);
		response.addProperty("msg", env.getContent().toString());
		return response;
	}

	/**
	 * Sets the preferred User Agent ID of cookies to use for requests to the given value, if they have permission
	 *
	 * @param context Current las2peer execution context
	 * @param ownerId User ID of preferred cookie owner
	 * @return Response indicating whether preference was updated successfully
	 */
	private JsonObject setUserPreference(ExecutionContext context, String ownerId) {
		JsonObject response = new JsonObject();
		UserAgent user = null;
		if (ownerId == null || ownerId.length() == 0) {
			ownerId = "";
		}
		try {
			user = (UserAgent) context.getMainAgent();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("status", 401);
			response.addProperty("msg", "Could not get user agent! Are you logged in?");
			return response;
		}

		// Unless owner is empty check consent first
		JsonArray permissions = ParserUtil.toJsonArray(getReader().getEntity().toString());
		if (ownerId.length() > 0 && (permissions == null ||
		  !permissions.contains(ParserUtil.toJsonElement(ownerId)))) {
			// No permission
			response.addProperty("status", 403);
			response.addProperty("msg","Lacking permissions to access requested cookies.");
			return response;
		}
		if (!L2pUtil.storeEnvelope(context, getPreferenceHandle(L2pUtil.getUserId(user)), ownerId, null, log)) {
			response.addProperty("status", 500);
			response.addProperty("msg", "Could not store preferences.");
			return response;
		}

		response.addProperty("status", 200);
		response.addProperty("msg", "Preference updated.");
		return response;
	}

	/**
	 * Initialize logger and (more importantly) smart contracts.
	 *
	 * @return Message whether initialization was successful
	 */
	@GET
	@Path("/init")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube/Init",
			notes = "Loads smart contracts in order to communicate with Ethereum blockchain")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response init() {
		if (initialized)
			return Response.status(400).entity("Service already initialized").build();

		if (debug != null && debug.equals("true"))
			log.setLevel(Level.ALL);
		else
			log.setLevel(Level.WARNING);

		log.info("Initializing service...");

		// In debug mode, use of blockchain is not enforced
		initialized = (idm.initialize((ExecutionContext) Context.getCurrent(), consentRegistryAddress,
				serviceAgentName, serviceAgentPw) || (debug != null && debug.equals("true")));
		if (!initialized)
			return buildResponse(500, "Initialization of smart contracts failed!");
		else
			return buildResponse(200, "Initialization successful.");
	}

	/**
	 * Main page showing some generally interesting YouTube videos
	 *
	 * @param ownerId A las2peer user ID to imitate a specific user
	 * @return Personalized YouTube recommendations
	 */
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube main page")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getMainPage(@QueryParam("user") String ownerId) {
		// Get execution context and set browser context (cookies and headers)
		ExecutionContext l2pContext;
		JsonObject response = new JsonObject();
		try {
			l2pContext = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Error getting execution context.");
			return buildResponse(500, response.toString());
		}

		BrowserContext context = browser.newContext();
		// Technically not what the request string was originally intended for, but useful for user study
		String request = L2pUtil.randomString(20);
		// TODO replace random String with requestUri from request data
		response = setContext(l2pContext, context, ownerId, request);
		if (!response.has("200"))
			return buildResponse(Integer.parseInt((String) response.keySet().toArray()[0]), response.toString());
		else
			response = new JsonObject();

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(YOUTUBE_MAIN_PAGE);
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug != null && debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				response.addProperty("500", "Could not get YouTube main page.");
				context.close();
				return buildResponse(500, response.toString());
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.mainPage(page.content());
			context.close();
			JsonArray responseBody = ParserUtil.toJsonArray(recommendations);
			JsonObject oneTimeCode = new JsonObject();
			oneTimeCode.addProperty("oneTimeCode", request);
			responseBody.add(oneTimeCode);
			return buildResponse(200, responseBody.toString());
		} catch (Exception e) {
			log.printStackTrace(e);
			context.close();
			response.addProperty("500", "Unspecified server error.");
			return buildResponse(500, response.toString());
		}
	}

	/**
	 * Recommendations shown in aside while watching the given video
	 *
	 * @param videoId The YouTube video ID of the currently playing video
	 * @param ownerId A las2peer user ID to imitate a specific user
	 * @return Personalized YouTube recommendations
	 */
	@GET
	@Path("/watch")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "YouTube/Watch",
			notes = "Returns YouTube 'watch next' recommendations")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getAside(@QueryParam("v") String videoId,
							 @QueryParam("user") String ownerId) {
		// Get execution context and set browser context (cookies and headers)
		ExecutionContext l2pContext;
		JsonObject response = new JsonObject();
		try {
			l2pContext = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Error getting execution context.");
			return buildResponse(500, response.toString());
		}

		if (videoId == null || videoId.length() == 0) {
			response.addProperty("400", "Missing video Id.");
			return buildResponse(400, response.toString());
		}

		BrowserContext context = browser.newContext();
		// Technically not what the request string was originally intended for, but useful for user study
		String request = L2pUtil.randomString(20);
		// TODO replace random String with requestUri from request data
		response = setContext(l2pContext, context, ownerId, request);
		if (!response.has("200"))
			return buildResponse(Integer.parseInt((String) response.keySet().toArray()[0]), response.toString());
		else
			response = new JsonObject();

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(getVideoUrl(videoId));
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug != null && debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				response.addProperty("500", "Could not get recommendations for video " + videoId);
				context.close();
				return buildResponse(500, response.toString());
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.aside(page.content());
			context.close();
			JsonArray responseBody = ParserUtil.toJsonArray(recommendations);
			JsonObject oneTimeCode = new JsonObject();
			oneTimeCode.addProperty("oneTimeCode", request);
			responseBody.add(oneTimeCode);
			return buildResponse(200, responseBody.toString());
		} catch (Exception e) {
			log.printStackTrace(e);
			context.close();
			response.addProperty("500", "Unspecified server error.");
			return buildResponse(500, response.toString());
		}
	}

	/**
	 * YouTube video search results personalized for the current user
	 *
	 * @param searchQuery The entered search query
	 * @param ownerId A las2peer user ID to imitate a specific user
	 * @return Personalized YouTube video search results
	 */
	@GET
	@Path("/results")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "YouTube/Results",
			notes = "Returns YouTube search results")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getSearchResults(@QueryParam("search_query") String searchQuery,
									 @QueryParam("user") String ownerId) {
		// Get execution context and set browser context (cookies and headers)
		ExecutionContext l2pContext;
		JsonObject response = new JsonObject();
		try {
			l2pContext = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Error getting execution context.");
			return buildResponse(500, response.toString());
		}

		if (searchQuery == null || searchQuery.length() == 0) {
			response.addProperty("400", "Missing search query.");
			return buildResponse(400, response.toString());
		}

		BrowserContext context = browser.newContext();
		// Technically not what the request string was originally intended for, but useful for user study
		String request = L2pUtil.randomString(20);
		// TODO replace random String with requestUri from request data
		response = setContext(l2pContext, context, ownerId, request);
		if (!response.has("200"))
			return buildResponse(Integer.parseInt((String) response.keySet().toArray()[0]), response.toString());
		else
			response = new JsonObject();

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(getResultsUrl(searchQuery));
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug != null && debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				response.addProperty("500", "Could not get YouTube results for query " + searchQuery);
				context.close();
				return buildResponse(500, response.toString());
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.resultsPage(page.content());
			context.close();
			JsonArray responseBody = ParserUtil.toJsonArray(recommendations);
			JsonObject oneTimeCode = new JsonObject();
			oneTimeCode.addProperty("oneTimeCode", request);
			responseBody.add(oneTimeCode);
			return buildResponse(200, responseBody.toString());
		} catch (Exception e) {
			log.printStackTrace(e);
			context.close();
			response.addProperty("500", "Unspecified server error");
			return buildResponse(500, response.toString());
		}
	}

	/**
	 * Function to retrieve the personal YouTube session cookies of the current user
	 *
	 * @return Returns an HTTP response signaling whether valid cookies are available for given user
	 */
	@GET
	@Path("/cookies")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Cookies",
			notes = "Returns current user's cookies")
	public Response getCookies() {
		JsonObject response = new JsonObject();
		if (!initialized) {
			return Response.serverError().entity("Service not initialized!").build();
		}

		JsonArray cookies = null;
		ExecutionContext context = null;
		UserAgent user = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
			user = (UserAgent) context.getMainAgent();
		} catch (Exception e) {
			return buildResponse(401, "Could not get execution context. Are you logged in?");
		}
		try {
			cookies = idm.getCookiesAsJson(context, L2pUtil.getUserId(user), rootUri, true);
			if (cookies == null) {
				return buildResponse(200, "No cookies found.");
			}
		} catch (Exception e) {
			return buildResponse(500, "Error getting cookies.");
		}

		// Check cookie validity
		response = validateCookies(cookies);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to store personal YouTube session cookies for the current user
	 *
	 * @param reqData The POST data containing the cookies to store and IDs of users allowed to read them
	 * @return Returns an HTTP response with plain text string content indicating whether storing was successful or not
	 */
	@POST
	@Path("/cookies")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Cookies",
			notes = "[{'name': 'cookie_name_1', 'value': 'cookie_value_1'}," +
					"{'name': 'cookie_name_2', 'value': 'cookie_value_2'}, ...],")
	public Response setCookies(String reqData) {
		if (!initialized)
			return Response.serverError().entity("Service not initialized!").build();

		ExecutionContext context = null;
		JsonArray cookies = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		try {
			cookies = ParserUtil.toJsonArray(reqData);
		} catch (Exception e) {
			return buildResponse(400, "Malformed POST data.");
		}

		// Check cookie validity
		JsonObject response = validateCookies(cookies);
		if (response.get("status").getAsInt() != 200)
			return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());

		response = idm.storeCookies(context, cookies);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to remove user specific YouTube cookies for current user
	 *
	 * @return Returns an HTTP response with plain text string content indicating whether revoking was successful or not
	 */
	@DELETE
	@Path("/cookies")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Cookies",
			notes = "Revokes requesting user's YouTube cookies")
	public Response revokeCookies() {
		if (!initialized)
			return buildResponse(500, "Service not initialized!");
		ExecutionContext context = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		JsonObject response = idm.removeCookies(context);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to store user specific HTTP headers for current user
	 *
	 * @param reqData The POST data containing the headers to store and IDs of users allowed to read them
	 * @return Returns an HTTP response with plain text string content indicating whether storing was successful or not
	 */
	@POST
	@Path("/headers")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Headers",
			notes = "{'header_name_1': 'header_value_1', 'header_value_2': 'header_value_2', ... }")
	public Response setHeaders(String reqData) {
		if (!initialized)
			return Response.serverError().entity("Service not initialized!").build();

		ExecutionContext context = null;
		JsonObject headers = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		try {
			headers = ParserUtil.toJsonObject(reqData);
		} catch (Exception e) {
			return buildResponse(400, "Malformed POST data.");
		}
		JsonObject response = idm.storeHeaders(context, headers);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to obtain user specific consent for the cookies associated with the storing user
	 *
	 * @return Returns an HTTP response with user's consent options as plain text string content
	 */
	@GET
	@Path("/consent")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Consent",
			notes = "Returns user's consent settings")
	public Response getConsent() {
		ExecutionContext context = null;
		JsonObject consentObj = null;
		JsonObject response = new JsonObject();
		if (!initialized) {
			response.addProperty("500", "Service not initialized!");
			return buildResponse(500, response.toString());
		}

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			response.addProperty("400", "Could not get execution context.");
			return buildResponse(400, response.toString());
		}
		response = idm.getConsent(context);
		if (response.get("status").getAsInt() == 200)
			return buildResponse(response.get("status").getAsInt(), response.get("msg").toString());
		return buildResponse(response.get("status").getAsInt(), response.toString());
	}

	/**
	 * Function to update user specific consent for the cookies associated with the storing user
	 *
	 * @param reqData The POST data necessary to create a consent object (reader, requestUri, and whether non-anonymous
	 *                   access is granted)
	 * @return Returns an HTTP response with plain text string content indicating whether updating was successful or not
	 */
	@POST
	@Path("/consent")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Consent",
			notes = "{'reader': reader_id, 'requestUri': request_uri:, 'anonymous:' (true/false)}")
	public Response updateConsent(String reqData) {
		ExecutionContext context = null;
		JsonObject consentObj = null;
		if (!initialized) {
			return buildResponse(500, "Service not initialized!");
		}

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		try {
			consentObj = ParserUtil.toJsonObject(reqData);
		} catch (Exception e) {
			return buildResponse(400, "Malformed POST data.");
		}
		JsonObject response = idm.updateConsent(context, consentObj);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to revoke user specific consent for the cookies associated with the storing user
	 *
	 * @param reqData The POST data necessary to create a consent object (reader, requestUri, and whether non-anonymous
	 *                   access is granted)
	 * @return Returns an HTTP response with plain text string content indicating whether revoking was successful or not
	 */
	@DELETE
	@Path("/consent")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Consent",
			notes = "{'reader': reader_id, 'requestUri': request_uri:, 'anonymous:' (true/false)}")
	public Response revokeConsent(String reqData) {
		ExecutionContext context = null;
		JsonObject consentObj = null;
		JsonObject response = new JsonObject();
		if (!initialized) {
			response.addProperty("500", "Service not initialized!");
			return buildResponse(500, response);
		}

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		try {
			consentObj = ParserUtil.toJsonObject(reqData);
		} catch (Exception e) {
			return buildResponse(400, "Malformed POST data.");
		}
		response = idm.revokeConsent(context, consentObj);
		if (response.get("status").getAsInt() != 200)
			return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
		// If anonymous consent is revoked, also revoke non-anonymous consent
		if (consentObj.get("anonymous").getAsBoolean()) {
			consentObj.addProperty("anonymous", false);
			response = idm.revokeConsent(context, consentObj);
		}
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to retrieve the non-anonymous access provided to the current user
	 *
	 * @return Returns an HTTP response with all IDs of the user who shared their cookies with the current user
	 */
	@GET
	@Path("/reader")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Reader",
			notes = "Returns user permissions")
	public Response getReader() {
		ExecutionContext context = null;
		HashSet<String> permissions = null;
		JsonObject response = new JsonObject();
		JsonArray ownerIds = new JsonArray();
		String readerId = null;
		if (!initialized) {
			response.addProperty("500", "Service not initialized!");
			return buildResponse(500, response.toString());
		}

		try {
			context = (ExecutionContext) Context.getCurrent();
			readerId = L2pUtil.getUserId((UserAgent) context.getMainAgent());
		} catch (Exception e) {
			response.addProperty("401", "Could not get user agent. Are you logged in?");
			return buildResponse(401, response.toString());
		}
		try {
			permissions = idm.getPermissions(context);
			if (permissions == null) {
				return buildResponse(200, new JsonArray().toString());
			}
			Iterator<String> it = permissions.iterator();
			while (it.hasNext()) {
				String ownerId = it.next();
				Consent consentObj = new Consent(ownerId, readerId, rootUri, false);
				if (idm.checkConsent(consentObj)) {
					ownerIds.add(ownerId);
				}
			}
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Could not get permissions.");
			return buildResponse(500, response.toString());
		}
		return buildResponse(200, ownerIds.toString());
	}

	/**
	 * Function to update access to users cookies and headers
	 *
	 * @param reqData The user IDs who are supposed to get access to the requesting user's cookies
	 * @return Returns an HTTP response with plain text string content indicating whether updating was successful or not
	 */
	@POST
	@Path("/reader")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Reader",
			notes = "[reader_id_1, reader_id_2, ... ]")
	public Response addReader(String reqData) {
		ExecutionContext context = null;
		JsonArray readerIds = null;
		if (!initialized)
			return buildResponse(500, "Service not initialized!");

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		try {
			readerIds = ParserUtil.toJsonArray(reqData);
		} catch (Exception e) {
			return buildResponse(400, "Malformed POST data.");
		}
		JsonObject response = idm.addReader(context, readerIds);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to revoke access to users cookies and headers
	 *
	 * @param reqData The user IDs who are supposed to lose access to the requesting user's cookies
	 * @return Returns an HTTP response with plain text string content indicating whether updating was successful or not
	 */
	@DELETE
	@Path("/reader")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Reader",
			notes = "[reader_id_1, reader_id_2, ... ]")
	public Response revokeReader(String reqData) {
		ExecutionContext context = null;
		JsonArray readerIds = null;
		JsonObject response = new JsonObject();
		if (!initialized) {
			response.addProperty("500", "Service not initialized!");
			return buildResponse(500, response);
		}

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		try {
			readerIds = ParserUtil.toJsonArray(reqData);
		} catch (Exception e) {
			return buildResponse(400, "Malformed POST data.");
		}
		response = idm.removeReader(context, readerIds);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to get foreign cookie preference of current user
	 *
	 * @return Returns an HTTP response with prefered user ID as plain text
	 */
	@GET
	@Path("/preference")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Reader",
			notes = "reader_id")
	public Response getCookiePreference() {
		ExecutionContext context = null;
		if (!initialized)
			return buildResponse(500, "Service not initialized!");

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		JsonObject response = getUserPreference(context);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to set foreign cookie preference
	 *
	 * @param ownerId The user IDs whose recommendations, the requesting user would like to use
	 * @return Returns an HTTP response with plain text string content indicating whether updating was successful or not
	 */
	@POST
	@Path("/preference")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Reader",
			notes = "reader_id")
	public Response addCookiePreference(String ownerId) {
		ExecutionContext context = null;
		if (!initialized)
			return buildResponse(500, "Service not initialized!");

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		JsonObject response = setUserPreference(context, ownerId);
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}

	/**
	 * Function to set foreign cookie preference to empty string
	 *
	 * @return Returns an HTTP response with plain text string content indicating whether updating was successful or not
	 */
	@DELETE
	@Path("/preference")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	@ApiOperation(
			value = "YouTube/Reader",
			notes = "reader_id")
	public Response removeCookiePreference() {
		ExecutionContext context = null;
		if (!initialized)
			return buildResponse(500, "Service not initialized!");

		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			return buildResponse(400, "Could not get execution context.");
		}
		JsonObject response = setUserPreference(context, "");
		return buildResponse(response.get("status").getAsInt(), response.get("msg").getAsString());
	}
}
