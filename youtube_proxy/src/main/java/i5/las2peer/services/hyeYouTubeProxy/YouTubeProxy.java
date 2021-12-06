package i5.las2peer.services.hyeYouTubeProxy;

import java.net.HttpURLConnection;

import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.logging.Level;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import i5.las2peer.api.Context;
import i5.las2peer.execution.ExecutionContext;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.hyeYouTubeProxy.lib.IdentityManager;
import i5.las2peer.services.hyeYouTubeProxy.lib.YouTubeParser;
import i5.las2peer.services.hyeYouTubeProxy.lib.Recommendation;
import i5.las2peer.services.hyeYouTubeProxy.lib.Util;

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
				version = "0.1",
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

	private final String ROOT_URI = "http://localhost:8080/youtube";
	private final String YOUTUBE_MAIN_PAGE = "https://www.youtube.com/";
	private final String YOUTUBE_VIDEO_PAGE = YOUTUBE_MAIN_PAGE + "watch?v=";
	private final String YOUTUBE_RESULTS_PAGE = YOUTUBE_MAIN_PAGE + "results?search_query=";
	private String debug;
	private String cookieFile;
	private String headerFile;
	private static Browser browser;
	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
	private IdentityManager idm;

	/**
	 * Class constructor, initializes member variables
	 */
	public YouTubeProxy() {
		setFieldValues();
		log.info("Got properties: debug = " + debug + ", cookieFile = " + cookieFile + ", headerFile = " + headerFile);
//		Cannot access logger of class instance before instance is created...
//		if (debug)
//			log.setLevel(Level.ALL);
		Playwright playwright = Playwright.create();
		browser = playwright.chromium().launch();
		idm = new IdentityManager(cookieFile, headerFile);
	}

	private String getVideoUrl(String videoId) {
		return YOUTUBE_VIDEO_PAGE + videoId;
	}

	private String getResultsUrl(String searchQuery) {
		return YOUTUBE_RESULTS_PAGE + searchQuery;
	}

	/**
	 * Main page showing some generally interesting YouTube videos
	 *
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
	public Response getMainPage() {
		// TODO put this somewhere more appropriate
		if (debug.equals("true"))
			log.setLevel(Level.ALL);

		ExecutionContext l2pContext;
		try {
			l2pContext = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Error getting execution context.");
			return Response.serverError().entity(response.toString()).build();
		}

		BrowserContext context = browser.newContext();

		try {
			context.addCookies(idm.getCookies(l2pContext));
			context.setExtraHTTPHeaders(idm.getHeaders(l2pContext));
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Error setting request context.");
			return Response.serverError().entity(response.toString()).build();
		}

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(YOUTUBE_MAIN_PAGE);
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				JsonObject response = new JsonObject();
				response.addProperty("500", "Could not get YouTube main page.");
				return Response.serverError().entity(response.toString()).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.mainPage(page.content());
			return Response.ok().entity(Util.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Unspecified server error.");
			return Response.serverError().entity(response.toString()).build();
		}
	}

	/**
	 * Recommendations shown in aside while watching the given video
	 *
	 * @param videoId The YouTube video ID of the currently playing video
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
	public Response getAside(@QueryParam("v") String videoId) {
		// TODO put this somewhere more appropriate
		if (debug.equals("true"))
			log.setLevel(Level.ALL);

		if (videoId.length() == 0) {
			JsonObject response = new JsonObject();
			response.addProperty("400", "Missing video Id.");
			return Response.status(400).entity(response.toString()).build();
		}

		ExecutionContext l2pContext;
		try {
			l2pContext = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			JsonObject response = new JsonObject();
			response.addProperty("401", "Could not get execution context. Are you logged in?");
			return Response.status(401).entity(response.toString()).build();
		}

		BrowserContext context = browser.newContext();

		try {
			context.addCookies(idm.getCookies(l2pContext));
			context.setExtraHTTPHeaders(idm.getHeaders(l2pContext));
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Error setting request context.");
			return Response.serverError().entity(response.toString()).build();
		}

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(YOUTUBE_MAIN_PAGE);
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				JsonObject response = new JsonObject();
				response.addProperty("500", "Could not get recommendations for video " + videoId);
				return Response.serverError().entity(response.toString()).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.aside(page.content());
			return Response.ok().entity(Util.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Unspecified server error.");
			return Response.serverError().entity(response.toString()).build();
		}
	}

	/**
	 * YouTube video search results personalized for the current user
	 *
	 * @param searchQuery The entered search query
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
	public Response getSearchResults(@QueryParam("search_query") String searchQuery) {
		// TODO put this somewhere more appropriate
		if (debug.equals("true"))
			log.setLevel(Level.ALL);

		if (searchQuery.length() == 0) {
			JsonObject response = new JsonObject();
			response.addProperty("400", "Missing search query.");
			return Response.status(400).entity(response.toString()).build();
		}

		ExecutionContext l2pContext;
		try {
			l2pContext = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			JsonObject response = new JsonObject();
			response.addProperty("401", "Could not get execution context. Are you logged in?");
			return Response.status(401).entity(response.toString()).build();
		}

		BrowserContext context = browser.newContext();

		try {
			context.addCookies(idm.getCookies(l2pContext));
			context.setExtraHTTPHeaders(idm.getHeaders(l2pContext));
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Error setting request context.");
			return Response.serverError().entity(response.toString()).build();
		}

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(YOUTUBE_MAIN_PAGE);
			// Wait until all content is loaded (doesn't seem to work that well, so let's skip it)
			// page.waitForLoadState(LoadState.NETWORKIDLE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				JsonObject response = new JsonObject();
				response.addProperty("500", "Could not get YouTube results for query " + searchQuery);
				return Response.serverError().entity(response.toString()).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.resultsPage(page.content());
			return Response.ok().entity(Util.toJsonString(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			JsonObject response = new JsonObject();
			response.addProperty("500", "Unspecified server error");
			return Response.serverError().entity(response.toString()).build();
		}
	}

	/**
	 * Function to store personal YouTube session cookies for the current user
	 * 
	 * @param cookies The POST data containing the cookies to store
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
					"{'name': 'cookie_name_2', 'value': 'cookie_value_2'}, ...]")
	public Response setCookies(String cookies) {
		ExecutionContext context = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			Response.status(401).entity("Could not get execution context. Are you logged in?").build();
		}
		JsonObject response = idm.storeCookies(context, Util.toJsonArray(cookies));
		return Response.status(response.get("status").getAsInt()).entity(response.get("msg").getAsString()).build();
	}

	/**
	 * Function to store user specific HTTP headers for current user
	 *
	 * @param headers The POST data containing the headers to store
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
	public Response setHeaders(String headers) {
		ExecutionContext context = null;
		try {
			context = (ExecutionContext) Context.getCurrent();
		} catch (Exception e) {
			Response.status(401).entity("Could not get execution context. Are you logged in?").build();
		}
		JsonObject response = idm.storeHeaders(context, Util.toJsonObject(headers));
		return Response.status(response.get("status").getAsInt()).entity(response.get("msg").getAsString()).build();
	}

	// TODO your own service methods, e. g. for RMI

}
