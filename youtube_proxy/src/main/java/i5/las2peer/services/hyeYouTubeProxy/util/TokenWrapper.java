package i5.las2peer.services.hyeYouTubeProxy.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import javax.xml.bind.annotation.XmlRootElement;

// Class is just needed since Google Token is not parsed correctly by JAX-RS
@XmlRootElement
public class TokenWrapper {
    private String access_token;
    private Integer expires_in;
    private String refresh_token;
    private String scope;
    private String token_type;

    public TokenWrapper() {
    }

    public TokenWrapper(String access_token, Integer expires_in, String refresh_token, String scope, String token_type) {
        this.access_token = access_token;
        this.expires_in = expires_in;
        this.refresh_token = refresh_token;
        this.scope = scope;
        this.token_type = token_type;
    }

    public TokenWrapper(Credential credential) {
        this.access_token = credential.getAccessToken();
        this.expires_in = credential.getExpiresInSeconds().intValue();
        this.refresh_token = credential.getRefreshToken();
        // Guessing these two
        // TODO Don't
        this.scope = "offline";
        this.token_type = "Bearer";
    }

    public String getAccess_token() {
        return access_token;
    }

    public Integer getExpires_in() {
        return expires_in;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public String getScope() {
        return scope;
    }

    public String getToken_type() {
        return token_type;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public void setExpires_in(Integer expires_in) {
        this.expires_in = expires_in;
    }

    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public TokenResponse googleToken() {
        return new TokenResponse().setAccessToken(access_token).setTokenType(token_type).setRefreshToken(refresh_token)
                .setScope(scope).setExpiresInSeconds(expires_in.longValue());
    }
    @Override
    public String toString() {
        return "TokenWrapper{" +
                "access_token='" + access_token + '\'' +
                ", expires_in=" + expires_in +
                ", refresh_token='" + refresh_token + '\'' +
                ", scope='" + scope + '\'' +
                ", token_type='" + token_type + '\'' +
                '}';
    }
}
