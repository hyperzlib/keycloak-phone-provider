package cc.coopersoft.keycloak.phone.providers.rest;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

@AutoService(RealmResourceProviderFactory.class)
public class GeetestResourceProvider implements RealmResourceProvider, RealmResourceProviderFactory {
    public static final String ID = "geetest";

    private final KeycloakSession session;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new GeetestResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

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
