package cc.coopersoft.keycloak.phone.utils;

import cc.coopersoft.keycloak.phone.providers.spi.ConfigService;
import org.keycloak.models.KeycloakSession;

public class ConfigUtils {
    public static int getOtpExpires(KeycloakSession session) {
        return session.getProvider(ConfigService.class).getTokenExpires();
    }

    public static boolean isDuplicatePhoneAllowed(KeycloakSession session) {
        ConfigService configService = session.getProvider(ConfigService.class);
        return configService.isDuplicatePhoneAllowed();
    }
}
