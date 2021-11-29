package i5.las2peer.services.hyeYouTubeProxy;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;

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

import okhttp3.Cookie;

import com.microsoft.playwright.*;

/**
 * HyE - YouTube Proxy
 * 
 * This service is used to obtain user data from YouTube via scraping.
 * To this end, it stores las2peer identities and associates them with YouTube cookies
 * which are used to obtain YouTube video recommendations.
 * 
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
	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());

	/**
	 * Class constructor, initializes member variables
	 */
	public YouTubeProxy() {

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
	 * Helper function to retrieve cookies for the given user
	 *
	 * @param user A las2peer user agent
	 * @return Valid YouTube cookies stored for that user
	 */
	private String getCookies(UserAgent user) {
		// TODO implement auth code storage
		return null;
	}

	/**
	 * Helper function to store the given cookies for the given user
	 *
	 * @param user A las2peer user agent
	 * @param cookies Google cookies
	 */
	private void storeAuthCode(UserAgent user, Cookie[] cookies) {
		// TODO implement auth code storage
		log.info("Cookies updated for user " + getUserId(user));
	}

	/**
	 * Main page showing some generally interesting YouTube videos
	 *
	 * @return Personalized YouTube recommendations
	 */
	@GET
	@Path("/")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube main page")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getMainPage() {
		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium().launch();
			BrowserContext context = browser.newContext();
			Page page = context.newPage();
			page.navigate(YOUTUBE_MAIN_PAGE);
			page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("test.png")));
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Unspecified server error").build();
		}
		return Response.ok().entity("OK").build();
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
