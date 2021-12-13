package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

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

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        formattedOutput = formatStringOutput();
    }

    public String getReaderId() {
        return readerId;
    }

    public void setReaderId(String readerId) {
        this.readerId = readerId;
        formattedOutput = formatStringOutput();
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

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
        formattedOutput = formatStringOutput();
    }

    public boolean isAnon() {
        return anon;
    }

    public void setAnon(boolean anon) {
        this.anon = anon;
        formattedOutput = formatStringOutput();
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
}