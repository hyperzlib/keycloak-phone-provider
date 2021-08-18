package cc.coopersoft.keycloak.phone.providers.jpa;

import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

import java.util.Collections;
import java.util.List;

public class TokenCodeJpaEntityProvider implements JpaEntityProvider {
    private static final Logger log = Logger.getLogger(TokenCodeJpaEntityProvider.class);

    @Override
    public List<Class<?>> getEntities() {
        log.info("get TokenCodeEntity");
        return Collections.<Class<?>>singletonList(TokenCodeEntity.class);
    }

    @Override
    public String getChangelogLocation() {
        return "META-INF/changelog/token-code-changelog.xml";
    }

    @Override
    public void close() {
    }

    @Override
    public String getFactoryId() {
        return TokenCodeJpaEntityProviderFactory.ID;
    }
}