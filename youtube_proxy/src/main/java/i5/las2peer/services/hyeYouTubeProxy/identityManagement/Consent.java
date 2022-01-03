package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

import com.google.gson.JsonObject;
import io.swagger.util.Json;

/**
 * Consent
 *
 * Class that defines object of consent by the given owner
 * for the given user to access the given identifier in
 * order to make a request to the given URI.
 *
 */
public class Consent {
    private String ownerId;
    private String readerId;
    // It does currently not seem necessary to store a consent object for every cookie
    // private String identifierHash;
    private String requestUri;
    private boolean anon;
    private String formattedOutput;

    public Consent(String ownerId, String readerId, String requestUri, boolean anon)
    {
        this.ownerId = ownerId;
        this.readerId = readerId;
        // this.identifierHash = identifierHash;
        this.requestUri = requestUri;
        this.anon = anon;
        formattedOutput = formatStringOutput();
    }

    public Consent(Consent consent)
    {
        this.ownerId = consent.getOwnerId();
        this.readerId = consent.getReaderId();
        // this.identifierHash = consent.getidentifierHash();
        this.requestUri = consent.getRequestUri();
        this.anon = consent.getAnon();
        formattedOutput = formatStringOutput();
    }

    public Consent(JsonObject json)
    {
        if (json.has("owner"))
            this.ownerId = json.get("owner").getAsString();
        if (json.has("reader"))
            this.readerId = json.get("reader").getAsString();
        if (json.has("request"))
            this.requestUri = json.get("request").getAsString();
        if (json.has("anon"))
            this.anon = json.get("anon").getAsBoolean();
        formattedOutput = formatStringOutput();
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Consent setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        formattedOutput = formatStringOutput();
        return this;
    }

    public String getReaderId() {
        return readerId;
    }

    public Consent setReaderId(String readerId) {
        this.readerId = readerId;
        formattedOutput = formatStringOutput();
        return this;
    }

    //    public String getIdentifierHash() {
    //        return identifierHash;
    //    }
    //
    //    public void setIdentifierHash(String identifierHash) {
    //        this.identifierHash = identifierHash;
    //        formattedOutput = formatStringOutput();
    //    }

    public String getRequestUri() {
        return requestUri;
    }

    public Consent setRequestUri(String requestUri) {
        this.requestUri = requestUri;
        formattedOutput = formatStringOutput();
        return this;
    }

    public boolean getAnon() {
        return anon;
    }

    public Consent setAnon(boolean anon) {
        this.anon = anon;
        formattedOutput = formatStringOutput();
        return this;
    }

    private String formatStringOutput() {
        return "{'owner':'" + ownerId +
                "','reader':'" + readerId +
                // "','identifier':'" + identifierHash +
                "','request':'" + requestUri +
                "','anonymous':'" + anon + "'}";
    }

    @Override
    public String toString() {
        return formattedOutput;
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.addProperty("owner", ownerId);
        result.addProperty("reader", readerId);
        result.addProperty("request", requestUri);
        result.addProperty("anonymous", anon);
        return result;
    }
}