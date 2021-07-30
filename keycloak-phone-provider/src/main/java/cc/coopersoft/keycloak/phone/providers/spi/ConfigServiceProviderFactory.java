package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.spi.impl.TokenCodeServiceImpl;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

public class ConfigServiceProviderFactory implements ProviderFactory<ConfigService> {
    private Config.Scope config;

    @Override
    public ConfigService create(KeycloakSession session) {
        return new ConfigService(session, config);
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
        return "config";
    }
}
