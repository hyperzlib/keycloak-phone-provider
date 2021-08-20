package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.AreaCodeService;
import cc.coopersoft.keycloak.phone.providers.spi.AreaCodeServiceProviderFactory;
import org.checkerframework.checker.units.qual.A;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

public class AreaCodeServiceProviderFactoryImpl implements AreaCodeServiceProviderFactory {
    private AreaCodeService instance;

    @Override
    public AreaCodeService create(KeycloakSession session) {
        if (instance == null) {
            instance = new AreaCodeService(session);
        }
        return instance;
    }

    @Override
    public void init(Config.Scope scope) {

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
