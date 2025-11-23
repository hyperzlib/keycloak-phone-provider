package cc.coopersoft.keycloak.phone.providers.spi;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.provider.Provider;
import org.keycloak.services.managers.AuthenticationManager;

public interface PhoneProviderCaptchaService extends Provider {
    boolean verify(final MultivaluedMap<String, String> formParams, String user);
    boolean verify(final MultivaluedMap<String, String> formParams, AuthenticationManager.AuthResult user);

    String getFrontendKey(String user);
    String getFrontendKey(AuthenticationManager.AuthResult user);
}
