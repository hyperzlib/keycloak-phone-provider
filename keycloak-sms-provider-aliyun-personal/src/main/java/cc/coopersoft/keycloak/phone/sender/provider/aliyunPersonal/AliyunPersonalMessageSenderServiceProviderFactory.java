package cc.coopersoft.keycloak.phone.sender.provider.aliyunPersonal;

import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderServiceProviderFactory;
import com.google.auto.service.AutoService;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.validation.Validation;

@AutoService(MessageSenderServiceProviderFactory.class)
public class AliyunPersonalMessageSenderServiceProviderFactory implements MessageSenderServiceProviderFactory {
    private static final Logger logger = Logger.getLogger(AliyunPersonalMessageSenderServiceProviderFactory.class);
    private Config.Scope config;

    @Override
    public MessageSenderService create(KeycloakSession keycloakSession) {
        return new AliyunPersonalMessageSenderServiceProvider(config, keycloakSession.getContext().getRealm());
    }

    @Override
    public void init(Config.Scope config) {
        this.config = config;
        logger.info("Initializing Aliyun Personal SMS Sender Provider Factory");
        if (Validation.isBlank(config.get("accessKeyId")) ||
                Validation.isBlank(config.get("accessKeySecret"))) {
            throw new IllegalStateException("Aliyun SMS Sender Provider requires accessKeyId and accessKeySecret configuration");
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "aliyunPersonal";
    }
}
