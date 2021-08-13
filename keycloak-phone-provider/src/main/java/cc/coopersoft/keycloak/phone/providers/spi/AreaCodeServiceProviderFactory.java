package cc.coopersoft.keycloak.phone.providers.spi;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

public class AreaCodeServiceProviderFactory implements ProviderFactory<AreaCodeService> {
    private Config.Scope config;

    @Override
    public AreaCodeService create(KeycloakSession session) {
        return new AreaCodeService(session);
    }

    @Override
    public void init(Config.Scope scope) {
        this.config = scope;
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "areacode";
    }
}
