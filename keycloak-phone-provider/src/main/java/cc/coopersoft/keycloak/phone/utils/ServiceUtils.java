package cc.coopersoft.keycloak.phone.utils;

import cc.coopersoft.keycloak.phone.providers.spi.ConfigService;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.validation.Validation;

import java.util.Map;

public class ServiceUtils {
    private static final Logger logger = Logger.getLogger(ServiceUtils.class);

    @Data
    @AllArgsConstructor
    public static class GetMessageSenderServiceResult {
        private final String id;
        private final MessageSenderService service;
    }

    public static GetMessageSenderServiceResult getMessageSenderService(KeycloakSession session, ConfigService config, int areaCode) {
        Map<Integer, String> areaCodeSenderMap = config.getAreaCodeSenderMap();
        if (areaCodeSenderMap != null && areaCodeSenderMap.containsKey(areaCode)) {
            String senderServiceId = areaCodeSenderMap.get(areaCode);
            MessageSenderService senderForAreaCode = session.getProvider(MessageSenderService.class, senderServiceId);
            if (senderForAreaCode != null) {
                return new GetMessageSenderServiceResult(senderServiceId, senderForAreaCode);
            }
        }

        if (!Validation.isBlank(config.getDefaultSender())) {
            MessageSenderService defaultSender = session.getProvider(MessageSenderService.class, config.getDefaultSender());
            if (defaultSender != null) {
                return new GetMessageSenderServiceResult(config.getDefaultSender(), defaultSender);
            }
        }

        logger.warn("defaultSender not configured or not found, using fallback MessageSenderService");
        MessageSenderService fallbackSender = session.getProvider(MessageSenderService.class);
        if (fallbackSender == null) {
            throw new IllegalStateException("No MessageSenderService provider available");
        }

        return new GetMessageSenderServiceResult("fallback", fallbackSender);
    }

    public static GetMessageSenderServiceResult getMessageSenderServiceResult(KeycloakSession session, int areaCode) {
        ConfigService config = session.getProvider(ConfigService.class);
        return getMessageSenderService(session, config, areaCode);
    }

    public static TokenCodeService getTokenCodeService(KeycloakSession session) {
        return session.getProvider(TokenCodeService.class);
    }
}
