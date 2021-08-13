package cc.coopersoft.keycloak.phone.providers.rest;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class GeetestResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public GeetestResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new GeetestResource(session);
    }

    @Override
    public void close() {
    }
}
