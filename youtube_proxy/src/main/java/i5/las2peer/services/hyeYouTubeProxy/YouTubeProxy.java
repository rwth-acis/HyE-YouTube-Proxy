package i5.las2peer.services.hyeYouTubeProxy;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonFactory;
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
import io.swagger.util.Json;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.model.*;

/**
 * HyE - YouTube Proxy
 * 
 * This service is used to obtain user data from YouTube via the YouTube Data API.
 * To this end, it stores las2peer identities and associates them with YouTube IDs
 * which are used to obtain YouTube recommendations and watch data.
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
@ServicePath("/youtube")

public class YouTubeProxy extends RESTService {

	private String clientId;
	private String clientSecret;
	private final String ROOT_URI = "http://localhost:8080/youtube/";
	private final String AUTH_URI = ROOT_URI + "auth";
	private final String REDIRECT_URI = "mymdb.org";
	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
	AuthorizationCodeFlow flow;

	public YouTubeProxy() {
		setFieldValues();
		log.info("Using client id " + this.clientId + " and secret " + this.clientSecret);
		flow = new Builder(new ApacheHttpTransport(), new GsonFactory(), this.clientId, this.clientSecret,
				Arrays.asList("https://www.googleapis.com/auth/youtube")).build();
	}

	/**
	 * Helper function to retrieve a user's YouTube ID
	 *
	 * @param user The User Agent whose YouTube ID we are interested in
	 * @return The YouTube ID linked to the given user
	 */

	private String getUserId(UserAgent user) {
		// TODO isolate sub from user agent or something like that
		return "1234";
	}

	private String getYouTubeId(UserAgent user) {
		// TODO implement using private las2peer storage
		return "1234";
	}

	/**
	 * Main page showing some YouTube data
	 *
	 * @return Some YouTube data
	 */
	@GET
	@Path("/")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Hello") })
	public Response getYouTubeData() {
		//TODO implement something
		UserAgent user = (UserAgent) Context.getCurrent().getMainAgent();
		return Response.ok().entity(user.getLoginName()).build();
	}

	/**
	 * Login function used to obtain access tokens or perform OAuth login.
	 * 
	 * @return A valid YouTube API Access Token or redirect to YouTube login.
	 */
	@GET
	@Path("/login")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Login",
			notes = "Performs an OAuth login to Google")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Got token") })
	public Response getAccessToken() {
		UserAgent user = (UserAgent) Context.getCurrent().getMainAgent();
		Credential creds = null;
		try {
			creds = flow.loadCredential(getYouTubeId(user));

			//TODO improve error handling
		} catch (Exception e) {
			log.severe(e.toString());
		}

		if (creds == null) {
			String redirectUrl = flow.newAuthorizationUrl().setRedirectUri(AUTH_URI).build();
			log.info("Redirecting to " + redirectUrl);
			return Response.temporaryRedirect(URI.create(redirectUrl)).build();
		} else {
			// TODO check if token is still valid before returning
			log.info("Got access token " + creds.getAccessToken());
			return Response.ok().entity(creds.getAccessToken()).build();
		}
	}

	/**
	 * Auth function used to handle YouTube OAuth logins.
	 *
	 * @param code The authentication code returned by Google.
	 * @return A valid YouTube API Access Token.
	 */
	@GET
	@Path("/auth")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Authentication",
			notes = "Handles the OAuth login to Google")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Got token") })
	public Response handleLogin(@DefaultValue("") @QueryParam("code") String code) {
		UserAgent user = (UserAgent) Context.getCurrent().getMainAgent();
		try {
			log.info("Sending token request");
			GoogleTokenResponse token = (GoogleTokenResponse) flow.newTokenRequest(code).setRedirectUri(ROOT_URI)
					.execute();
			log.info("Storing token " + token.getIdToken());
			Credential creds = flow.createAndStoreCredential(token, getUserId(user));
			return Response.ok().entity(creds.getAccessToken()).build();

			// TODO imporve error handling
		} catch (Exception e) {
			log.severe(e.toString());
			return Response.serverError().build();
		}
	}

	/**
	 * Function to finish login.
	 *
	 * @return Nothing much
	 */
	@GET
	@Path("/token")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Finished",
			notes = "YouTube login finished")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Login successful") })
	public Response handleToken() {
		return Response.ok().build();
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
