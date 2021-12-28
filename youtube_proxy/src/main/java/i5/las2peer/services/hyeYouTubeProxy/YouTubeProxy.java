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
				version = "0.1.9",
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

	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
	private static Browser browser = null;
	private static boolean initialized = false;
	private static IdentityManager idm = null;
	private static Random rand = null;
	private String videoUri;
	private String searchUri;

	/**
	 * Class constructor, initializes member variables
	 */
	public YouTubeProxy() {
		setFieldValues();
		log.info("Got properties: debug = " + debug + ", cookieFile = " + cookieFile + ", headerFile = " +
				headerFile + ", consentRegistryAddress = " + consentRegistryAddress + ", rootUri = " + rootUri);
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
			Random rand = new Random();
			rand.setSeed(System.currentTimeMillis());
		}
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

		// If nothing else works, return random agent
		Iterator<String> it = candidates.iterator();
		int randPos = rand.nextInt(candidates.size());
		String userId = "";
		while (it.hasNext() && randPos > 0) {
			userId = it.next();
			--randPos;
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
			return Response.serverError().entity("Initialization of smart contracts failed!").build();
		else
			return Response.status(200).entity("Initialization successful.").build();
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
			return Response.serverError().entity(response.toString()).build();
		}

		BrowserContext context = browser.newContext();
		response = setContext(l2pContext, context, ownerId, rootUri);
		if (!response.has("200"))
			return Response.status(response.get((String) response.keySet().toArray()[0]).getAsInt())
					.entity(response.toString()).build();

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
				return Response.serverError().entity(response.toString()).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.mainPage(page.content());
			return Response.ok().entity(ParserUtil.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Unspecified server error.");
			return Response.serverError().entity(response.toString()).build();
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
			return Response.serverError().entity(response.toString()).build();
		}

		if (videoId == null || videoId.length() == 0) {
			response.addProperty("400", "Missing video Id.");
			return Response.status(400).entity(response.toString()).build();
		}

		BrowserContext context = browser.newContext();
		response = setContext(l2pContext, context, ownerId, videoUri);
		if (!response.has("200"))
			return Response.status(response.get((String) response.keySet().toArray()[0]).getAsInt())
					.entity(response.toString()).build();

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
				return Response.serverError().entity(response.toString()).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.aside(page.content());
			return Response.ok().entity(ParserUtil.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Unspecified server error.");
			return Response.serverError().entity(response.toString()).build();
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
			return Response.serverError().entity(response.toString()).build();
		}

		if (searchQuery == null || searchQuery.length() == 0) {
			response.addProperty("400", "Missing search query.");
			return Response.status(400).entity(response.toString()).build();
		}

		BrowserContext context = browser.newContext();
		response = setContext(l2pContext, context, ownerId, searchUri);
		if (!response.has("200"))
			return Response.status(response.get((String) response.keySet().toArray()[0]).getAsInt())
					.entity(response.toString()).build();

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
				return Response.serverError().entity(response.toString()).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.resultsPage(page.content());
			return Response.ok().entity(ParserUtil.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			response.addProperty("500", "Unspecified server error");
			return Response.serverError().entity(response.toString()).build();
		}
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
			notes = "{'cookies': [{'name': 'cookie_name_1', 'value': 'cookie_value_1'}," +
					"{'name': 'cookie_name_2', 'value': 'cookie_value_2'}, ...]," +
					"'readers': ['username_of_reader_1', 'username_of_reader_2', ... ]")
	public Response setCookies(String reqData) {
		ExecutionContext context = null;
		JsonArray cookies = null;
		JsonArray readers = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			Response.status(401).entity("Could not get execution context. Are you logged in?").build();
		}
		try {
			JsonObject reqJsonData = ParserUtil.toJsonObject(reqData);
			cookies = reqJsonData.get("cookies").getAsJsonArray();
			readers = reqJsonData.get("readers").getAsJsonArray();
		} catch (Exception e) {
			Response.status(400).entity("Malformed POST data.").build();
		}
		JsonObject response = idm.storeCookies(context, cookies, ParserUtil.jsonToArrayList(readers));
		return Response.status(response.get("status").getAsInt()).entity(response.get("msg").getAsString()).build();
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
			notes = "{'headers': {'header_name_1': 'header_value_1', 'header_value_2': 'header_value_2', ... }," +
					"'readers': ['username_of_reader_1', 'username_of_reader_2', ... ]")
	public Response setHeaders(String reqData) {
		ExecutionContext context = null;
		JsonObject headers = null;
		JsonArray readers = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			Response.status(401).entity("Could not get execution context. Are you logged in?").build();
		}
		try {
			JsonObject reqJsonData = ParserUtil.toJsonObject(reqData);
			headers = reqJsonData.get("headers").getAsJsonObject();
			readers = reqJsonData.get("readers").getAsJsonArray();
		} catch (Exception e) {
			Response.status(400).entity("Malformed POST data.").build();
		}
		JsonObject response = idm.storeHeaders(context, headers, ParserUtil.jsonToArrayList(readers));
		return Response.status(response.get("status").getAsInt()).entity(response.get("msg").getAsString()).build();
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
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			Response.status(401).entity("Could not get execution context. Are you logged in?").build();
		}
		try {
			consentObj = ParserUtil.toJsonObject(reqData);
		} catch (Exception e) {
			Response.status(400).entity("Malformed POST data.").build();
		}
		JsonObject response = idm.updateConsent(context, consentObj);
		return Response.status(response.get("status").getAsInt()).entity(response.get("msg").getAsString()).build();
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
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			Response.status(401).entity("Could not get execution context. Are you logged in?").build();
		}
		try {
			consentObj = ParserUtil.toJsonObject(reqData);
		} catch (Exception e) {
			Response.status(400).entity("Malformed POST data.").build();
		}
		JsonObject response = idm.revokeConsent(context, consentObj);
		return Response.status(response.get("status").getAsInt()).entity(response.get("msg").getAsString()).build();
	}
}
