package i5.las2peer.services.hyeYouTubeProxy;

import java.io.Serializable;
import java.net.HttpURLConnection;

import java.nio.file.Paths;

import java.util.*;
import java.util.logging.Level;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.microsoft.playwright.options.Cookie;
import i5.las2peer.api.Context;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.execution.ExecutionContext;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.hyeYouTubeProxy.identityManagement.Consent;
import i5.las2peer.services.hyeYouTubeProxy.identityManagement.IdentityManager;
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

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
// import com.microsoft.playwright.options.LoadState;

/**
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
				version = "0.1.13",
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

	private String debug;
	private String cookieFile;
	private String headerFile;
	private String consentRegistryAddress;
	private String rootUri;
	private String frontendUrls;

	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
	private static Browser browser = null;
	private static boolean initialized = false;
	private static IdentityManager idm = null;
	private static Random rand = null;
	private String videoUri;
	private String searchUri;
	private ArrayList<String> corsAddresses;

	/**
	 * Class constructor, initializes member variables
	 */
	public YouTubeProxy() {
		setFieldValues();
		log.info("Got properties: debug = " + debug + ", cookieFile = " + cookieFile + ", headerFile = " +
				headerFile + ", consentRegistryAddress = " + consentRegistryAddress + ", rootUri = " + rootUri +
				", frontendUrls = " + frontendUrls);
		// Add trailing slash, if not already there
		if (rootUri.charAt(rootUri.length()-1) != '/')
			rootUri += '/';
		videoUri = rootUri + "watch";
		searchUri = rootUri + "results";

		if (browser == null) {
			Playwright playwright = Playwright.create();
			browser = playwright.chromium().launch();
		}
		if (idm == null) {
			// Do not allow to use static cookies/headers for production
			if (debug.equals("true"))
				idm = new IdentityManager(cookieFile, headerFile);
			else
				idm = new IdentityManager(null, null);
		}
		if (rand == null) {
			rand = new Random();
			rand.setSeed(System.currentTimeMillis());
		}
		corsAddresses = new ArrayList<String>(Arrays.asList(frontendUrls.split(",")));
	}

	private String getVideoUrl(String videoId) {
		return YOUTUBE_VIDEO_PAGE + videoId;
	}

	private String getResultsUrl(String searchQuery) {
		return YOUTUBE_RESULTS_PAGE + searchQuery;
	}

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

		try {
			// RMI call with parameters
			return (String) context.invoke(
					"i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations", "findMatch",
					new Serializable[] { candidates, request });
		} catch (Exception e) {
			log.printStackTrace(e);
		}

		// If recommendation service isn't running, try to get random user
		// TODO repeat if chosen user did not consent to this particular request
		String userId = "";
		if (candidates != null && candidates.size() > 0) {
			Iterator<String> it = candidates.iterator();
			int randPos = rand.nextInt(candidates.size());
			while (it.hasNext() && randPos >= 0) {
				userId = it.next();
				--randPos;
			}
		}

		// Return current user if even this didn't yield a result
		return userId.length() > 0 ? userId : idm.getUserId((UserAgent) context.getMainAgent());
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
			response.addProperty("500", "Service not initialized!");
			return response;
		}

		// Get cookies (and headers) of appropriate user
		boolean anon = (ownerId == null || ownerId.length() == 0);
		if (anon)
			ownerId = findMatch(l2pContext, request);

		ArrayList<Cookie> cookies = idm.getCookies(l2pContext, ownerId, request, anon);
		HashMap<String, String> headers = idm.getHeaders(l2pContext, ownerId, request, anon);

		if (cookies == null) {
			response.addProperty("500", "Could not retrieve cookies.");
			return response;
		}
		if (cookies.isEmpty()) {
			response.addProperty("401", "Lacking consent for request.");
			return response;
		}

		try {
			browserContext.addCookies(cookies);
			browserContext.setExtraHTTPHeaders(headers);
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Error setting request context.");
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

		if (debug.equals("true"))
			log.setLevel(Level.ALL);
		else
			log.setLevel(Level.WARNING);

		log.info("Initializing service...");
		initialized = idm.initialize((ExecutionContext) Context.getCurrent(), consentRegistryAddress);
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
		response = setContext(l2pContext, context, ownerId, rootUri);
		if (!response.has("200"))
			return buildResponse(Integer.parseInt((String) response.keySet().toArray()[0]), response.toString());

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(YOUTUBE_MAIN_PAGE);
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				response.addProperty("500", "Could not get YouTube main page.");
				return buildResponse(500, response.toString());
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.mainPage(page.content());
			return buildResponse(200, ParserUtil.toJsonString(recommendations));
		} catch (Exception e) {
			log.printStackTrace(e);
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
		response = setContext(l2pContext, context, ownerId, videoUri);
		if (!response.has("200"))
			return buildResponse(Integer.parseInt((String) response.keySet().toArray()[0]), response.toString());

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(getVideoUrl(videoId));
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				response.addProperty("500", "Could not get recommendations for video " + videoId);
				return buildResponse(500, response.toString());
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.aside(page.content());
			return Response.ok().entity(ParserUtil.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
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
		response = setContext(l2pContext, context, ownerId, searchUri);
		if (!response.has("200"))
			return buildResponse(Integer.parseInt((String) response.keySet().toArray()[0]), response.toString());

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(getResultsUrl(searchQuery));
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				response.addProperty("500", "Could not get YouTube results for query " + searchQuery);
				return buildResponse(500, response.toString());
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.resultsPage(page.content());
			return buildResponse(200, ParserUtil.toJsonString(recommendations));
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Unspecified server error");
			return buildResponse(500, response.toString());
		}
	}

	/**
	 * Function to retrieve the personal YouTube session cookies of the current user
	 *
	 * @return Returns an HTTP response containing the stored cookies as Json
	 */
	@GET
	@Path("/cookies")
	@Produces(MediaType.APPLICATION_JSON)
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
			response.addProperty("500", "Service not initialized!");
			return Response.serverError().entity(response.toString()).build();
		}

		JsonArray cookies = null;
		ExecutionContext context = null;
		UserAgent user = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
			user = (UserAgent) context.getMainAgent();
		} catch (Exception e) {
			response.addProperty("401", "Could not get execution context. Are you logged in?");
			return buildResponse(401, response.toString());
		}
		try {
			cookies = idm.getCookiesAsJson(context, idm.getUserId(user), rootUri, true);
			if (cookies == null) {
				return buildResponse(200, new JsonArray().toString());
			}
		} catch (Exception e) {
			response.addProperty("500", "Error getting cookies.");
			return buildResponse(500, response.toString());
		}
		return buildResponse(200, cookies.toString());
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
		JsonObject response = idm.storeCookies(context, cookies);
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
		// If non-anonymously consent is revoked, also revoke anonymous consent
		if (!consentObj.get("anonymous").getAsBoolean()) {
			consentObj.addProperty("anonymous", true);
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
			readerId = idm.getUserId((UserAgent) context.getMainAgent());
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
				if (idm.checkConsent(consentObj) || idm.checkConsent(consentObj.setRequestUri(videoUri)) ||
						idm.checkConsent(consentObj.setRequestUri(searchUri))) {
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
}
