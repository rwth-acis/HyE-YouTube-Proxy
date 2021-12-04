package i5.las2peer.services.hyeYouTubeProxy;

import java.net.HttpURLConnection;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import i5.las2peer.api.Context;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

import i5.las2peer.services.hyeYouTubeProxy.lib.IdentityManager;
import i5.las2peer.services.hyeYouTubeProxy.lib.YouTubeParser;
import i5.las2peer.services.hyeYouTubeProxy.lib.Recommendation;

import com.google.gson.Gson;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

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
	private Gson gson;

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
		gson = new Gson();
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

		UserAgent user;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();
		} catch (Exception e) {
			return Response.status(401).entity(gson.toJson(
					new HashMap<String, String>().put("401", "Could not get user agent. Are you logged in?"))).build();
		}

		BrowserContext context = browser.newContext();

		try {
			context.addCookies(idm.getCookies(user));
			context.setExtraHTTPHeaders(idm.getHeaders(user));
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity(
					gson.toJson(new HashMap<String, String>().put("500", "Error setting request context"))).build();
		}

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(YOUTUBE_MAIN_PAGE);
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				return Response.serverError().entity(gson.toJson(
						new HashMap<String, String>().put("500", "Could not get YouTube main page"))).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.mainPage(page.content());
			return Response.ok().entity(gson.toJson(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity(
					gson.toJson(new HashMap<String, String>().put("500", "Unspecified server error"))).build();
		}
	}

	/**
	 * Recommendations shown in aside while watching the given video
	 *
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

		if (videoId.length() == 0)
			return Response.status(400).entity(
					gson.toJson(new HashMap<String, String>().put("400", "Missing video Id"))).build();

		UserAgent user;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();
		} catch (Exception e) {
			return Response.status(401).entity(gson.toJson(
					new HashMap<String, String>().put("401", "Could not get user agent. Are you logged in?"))).build();
		}

		BrowserContext context = browser.newContext();

		try {
			context.addCookies(idm.getCookies(user));
			context.setExtraHTTPHeaders(idm.getHeaders(user));
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity(
					gson.toJson(new HashMap<String, String>().put("500", "Error setting request context"))).build();
		}

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(getVideoUrl(videoId));
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				return Response.serverError().entity(gson.toJson(new HashMap<String, String>()
						.put("500", "Could not get recommendations for video " + videoId))).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.aside(page.content());
			return Response.ok().entity(gson.toJson(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity(
					gson.toJson(new HashMap<String, String>().put("500", "Unspecified server error"))).build();
		}
	}

	/**
	 * Main page showing some generally interesting YouTube videos
	 *
	 * @return Personalized YouTube recommendations
	 */
	@GET
	@Path("/results")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube search results")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getSearchResults(@QueryParam("search_query") String searchQuery) {
		// TODO put this somewhere more appropriate
		if (debug.equals("true"))
			log.setLevel(Level.ALL);

		if (searchQuery.length() == 0)
			return Response.status(400).entity(
					gson.toJson(new HashMap<String, String>().put("400", "Missing search query"))).build();

		UserAgent user;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();
		} catch (Exception e) {
			return Response.status(401).entity(gson.toJson(
					new HashMap<String, String>().put("401", "Could not get user agent. Are you logged in?"))).build();
		}

		BrowserContext context = browser.newContext();

		try {
			context.addCookies(idm.getCookies(user));
			context.setExtraHTTPHeaders(idm.getHeaders(user));
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity(
					gson.toJson(new HashMap<String, String>().put("500", "Error setting request context"))).build();
		}

		try {
			Page page = context.newPage();
			com.microsoft.playwright.Response resp = page.navigate(getResultsUrl(searchQuery));
			if (debug.equals("true"))
				page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
			if (resp.status() != 200) {
				log.severe(resp.statusText());
				return Response.serverError().entity(gson.toJson(
						new HashMap<String, String>()
								.put("500", "Could not get YouTube results for query " + searchQuery))).build();
			}
			ArrayList<Recommendation> recommendations = YouTubeParser.resultsPage(page.content());
			return Response.ok().entity(gson.toJson(recommendations)).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity(
					gson.toJson(new HashMap<String, String>().put("500", "Unspecified server error"))).build();
		}
	}

	/**
	 * Template of a post function.
	 * 
	 * @param myInput The post input the user will provide.
	 * @return Returns an HTTP response with plain text string content derived from the path input param.
	 */
	@POST
	@Path("/post/{input}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE") })
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "Example method that returns a phrase containing the received input.")
	public Response postTemplate(@PathParam("input") String myInput) {
		String returnString = "";
		returnString += "Input " + myInput;
		return Response.ok().entity(returnString).build();
	}

	// TODO your own service methods, e. g. for RMI

}
