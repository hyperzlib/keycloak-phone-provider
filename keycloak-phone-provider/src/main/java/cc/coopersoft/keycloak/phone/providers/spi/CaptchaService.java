package cc.coopersoft.keycloak.phone.providers.spi;

import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;
import org.keycloak.services.managers.AuthenticationManager;

import javax.ws.rs.core.MultivaluedMap;

public interface CaptchaService extends Provider {
    boolean verify(final MultivaluedMap<String, String> formParams, String user);
    boolean verify(final MultivaluedMap<String, String> formParams, AuthenticationManager.AuthResult user);

    String getFrontendKey(String user);
    String getFrontendKey(AuthenticationManager.AuthResult user);
}
