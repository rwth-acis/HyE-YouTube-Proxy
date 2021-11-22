package i5.las2peer.services.hyeYouTubeProxy;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import i5.las2peer.api.Context;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.hyeYouTubeProxy.util.TokenWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTube.*;
import com.google.api.services.youtube.YouTubeRequest;

import i5.las2peer.services.hyeYouTubeProxy.util.ErrorCodes;

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
	private final String ROOT_URI = "http://localhost:8080/youtube";
	private final String LOGIN_URI = ROOT_URI + "/login";
	private final String AUTH_URI = ROOT_URI + "/auth";
	private static final L2pLogger log = L2pLogger.getInstance(YouTubeProxy.class.getName());
	private AuthorizationCodeFlow flow;
	private YouTube ytConnection;
	private HttpTransport transport;
	private GsonFactory json;
	// Store access token in frontend instead
	// private HashMap<String, String> tokens;

	/**
	 * Class constructor, initializes member variables
	 */
	HttpRequestInitializer httpRequestInitializer;
	public YouTubeProxy() {
		setFieldValues();
		// Access token is stored by user
		// tokens = new HashMap<String, String>();
		log.info("Using client id " + clientId + " and secret " + clientSecret);
		transport = new ApacheHttpTransport();
		json = new GsonFactory();
		flow = new GoogleAuthorizationCodeFlow.Builder(transport, json, this.clientId, this.clientSecret,
				Arrays.asList("https://www.googleapis.com/auth/youtube")).setAccessType("offline").build();
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
	 * Helper function to retrieve an authorization code for the given user
	 *
	 * @param user A las2peer user agent
	 * @return Either a valid authorization code belonging to that user or an appropriate error message
	 */
	private String getAuthCode(UserAgent user) {
		// TODO implement auth code storage
		return null;
	}

	/**
	 * Helper function to store the given authorization code for the given user
	 *
	 * @param user A las2peer user agent
	 * @param code A Google Authorization code
	 */
	private void storeAuthCode(UserAgent user, String code) {
		// TODO implement auth code storage
		log.info("Authorization code updated for user " + getUserId(user));
	}

	/**
	 * Helper function to retrieve an access token with the given authorization code
	 *
	 * @param code A Google OAuth authorization code
	 * @return Either a valid access token or an appropriate error message
	 */
	private TokenResponse getAccessToken(String code) {
		TokenResponse token;
		log.info("Sending token request");

		try {
			token = flow.newTokenRequest(code).setRedirectUri(AUTH_URI).execute();
		} catch (Exception e) {
			//TODO improve error handling
			log.printStackTrace(e);
			return null;
		}
		return token;
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
	public Response getMainPage(@DefaultValue("") @QueryParam("code") String code) {
		// Check for access token of current user in memory
		UserAgent user;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Unable to get user agent. Are you logged in?").build();
		}

		Credential credential;
		try {
			credential = flow.loadCredential(getUserId(user));


			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Authentication failed").build();
		}

		if (credential == null) {
			return Response.status(401).entity("No valid access token for user.").build();
		}

		String response = "OK";
		try {
			ytConnection = new YouTube.Builder(transport, json, credential).setApplicationName("How's your Experience")
					.build();
			HttpRequest request = ytConnection.search().list("snippet").setType("video").buildHttpRequest();
			log.info("Sending request " + request.toString());
			response = request.execute().parseAsString();

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Unspecified server error").build();
		}
		return Response.ok().entity(response).build();
	}

	/**
	 * Main page showing some generally interesting YouTube videos
	 *
	 * @return Personalized YouTube recommendations
	 */
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube main page")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getMainPage(TokenWrapper token) {
		// Check for access token of current user in memory
		UserAgent user;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Unable to get user agent. Are you logged in?").build();
		}

		Credential credential;
		try {
			credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).setJsonFactory(json)
					.setTransport(transport)//.setClientAuthentication().setTokenServerUrl(new GenericUrl(""))
					.build().setFromTokenResponse(token.googleToken());

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(401).entity("Invalid access token " + token.toString()).build();
		}

		String response = "OK";
		try {
			ytConnection = new YouTube.Builder(transport, json, credential).setApplicationName("How's your Experience")
					.build();
			HttpRequest request = ytConnection.search().list("snippet").setType("video").buildHttpRequest();
			log.info("Sending request " + request.toString());
			response = request.execute().parseAsString();

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Unspecified server error").build();
		}
		return Response.ok().entity(response).build();
	}

	/**
	 * Login function used to perform OAuth login.
	 *
	 * @return A redirect to a Google OAuth page
	 */
	@GET
	@Path("/login")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Login",
			notes = "Sends user to Google login page")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response login() {
		UserAgent user = (UserAgent) Context.getCurrent().getMainAgent();
		String redirectUrl = flow.newAuthorizationUrl().setRedirectUri(AUTH_URI).build();
		log.info("Redirecting user " + getUserId(user) + " to " + redirectUrl);
		return Response.temporaryRedirect(URI.create(redirectUrl)).build();
	}

	/**
	 * Authentication function used to handle YouTube OAuth logins.
	 * TODO Since the auth code is sent as get parameter it is written to the server log, which is kind of a privacy breach
	 *
	 * @param code The authentication code returned by Google.
	 * @return A valid YouTube API Access Token
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
					message = "OK") })
	public Response auth(@DefaultValue("") @QueryParam("code") String code) {
		// Test authorization code
		TokenResponse token = getAccessToken(code);
		if (token == null) {
			return Response.serverError().entity("Could not get access token with authorization code " + code).build();
		}

		// Store authorization code
		UserAgent user = (UserAgent) Context.getCurrent().getMainAgent();
		storeAuthCode(user, code);
		Credential credential = null;
		try {
			credential = flow.createAndStoreCredential(token, getUserId(user));

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
		}


		// Currently, just here for testing
		// ***********************************
		try {
			ytConnection = new YouTube.Builder(transport, json, credential).setApplicationName("How's your Experience")
					.build();
			HttpRequest request = ytConnection.search().list("snippet").setType("video").buildHttpRequest();
			log.info("Sending request " + request.toString());
			String response = request.execute().parseAsString();
			log.info(response);

			// TODO improve error handling
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Unspecified server error").build();
		}
		// ***********************************

		return Response.ok().entity(new TokenWrapper(credential).toString()).build();
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
