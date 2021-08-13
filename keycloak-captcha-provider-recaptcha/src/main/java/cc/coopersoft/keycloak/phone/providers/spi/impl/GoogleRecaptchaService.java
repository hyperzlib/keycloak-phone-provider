package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.keycloak.Config;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.util.JsonSerialization;

import javax.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GoogleRecaptchaService implements CaptchaService {
    public static final String G_RECAPTCHA_RESPONSE = "g-recaptcha-response";

    private final KeycloakSession session;
    private Config.Scope config;

    public GoogleRecaptchaService(KeycloakSession session) {
        this.session = session;
    }

    private String getUserIdByAuthResult(AuthenticationManager.AuthResult user){
        String uid = "guest";
        if(user != null){
            uid = user.getUser().getId();
        }
        return uid;
    }

    @Override
    public boolean verify(final MultivaluedMap<String, String> formParams, AuthenticationManager.AuthResult user) {
        return this.verify(formParams, this.getUserIdByAuthResult(user));
    }

    @Override
    public boolean verify(final MultivaluedMap<String, String> formParams, String user) {
        boolean success = false;
        CloseableHttpClient httpClient = session.getProvider(HttpClientProvider.class).getHttpClient();
        HttpPost post = new HttpPost("https://www.recaptcha.net/recaptcha/api/siteverify");
        List<NameValuePair> formparams = new LinkedList<>();
        formparams.add(new BasicNameValuePair("secret", config.get("secret")));
        formparams.add(new BasicNameValuePair("response", formParams.getFirst(G_RECAPTCHA_RESPONSE)));
        formparams.add(new BasicNameValuePair("remoteip", session.getContext().getConnection().getRemoteAddr()));
        try {
            UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams, "UTF-8");
            post.setEntity(form);
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                InputStream content = response.getEntity().getContent();
                try {
                    Map json = JsonSerialization.readValue(content, Map.class);
                    Object val = json.get("success");
                    success = Boolean.TRUE.equals(val);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            }
        } catch (Exception e) {
            ServicesLogger.LOGGER.recaptchaFailed(e);
        }
        return success;
    }

    @Override
    public String getFrontendKey(AuthenticationManager.AuthResult user){
        return "";
    }

    @Override
    public String getFrontendKey(String user){
        return "";
    }

    public void setConfig(Config.Scope config){
        this.config = config;
    }

    @Override
    public void close() {
    }
}
