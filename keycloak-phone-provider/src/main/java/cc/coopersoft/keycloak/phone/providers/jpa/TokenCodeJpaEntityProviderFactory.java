package cc.coopersoft.keycloak.phone.providers.jpa;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class TokenCodeJpaEntityProviderFactory implements JpaEntityProviderFactory {
    private static final Logger log = Logger.getLogger(TokenCodeJpaEntityProviderFactory.class);
    public static String ID = "tokenCodeEntityProviderFactory";

    @Override
    public JpaEntityProvider create(KeycloakSession session) {
        log.info("Create TokenCodeJpaEntityProvider");
        return new TokenCodeJpaEntityProvider();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
