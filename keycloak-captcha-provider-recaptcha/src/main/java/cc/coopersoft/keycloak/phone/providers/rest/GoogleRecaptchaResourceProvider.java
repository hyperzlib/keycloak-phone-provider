package cc.coopersoft.keycloak.phone.providers.rest;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class GoogleRecaptchaResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public GoogleRecaptchaResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new GoogleRecaptchaResource(session);
    }

    @Override
    public void close() {
    }
}
