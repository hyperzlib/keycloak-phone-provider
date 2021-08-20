package cc.coopersoft.keycloak.phone.providers.spi;

import lombok.Getter;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;

@Getter
public class ConfigService implements Provider {
    public final String senderService;
    public final int tokenExpires;
    public final int defaultAreaCode;
    public final String areaCodeConfig;
    public final boolean areaLocked;
    public final boolean allowUnset;

    public ConfigService(Config.Scope config){
        this.senderService = config.get("senderService", "dummy");
        this.tokenExpires = config.getInt("tokenExpires", 300);
        this.defaultAreaCode = config.getInt("defaultAreacode", 86);
        this.areaCodeConfig = config.get("areacodeConfig", "./areacode.json");
        this.areaLocked = config.getBoolean("areaLocked", false);
        this.allowUnset = config.getBoolean("allowUnset", true);
    }

    @Override
    public void close() {

    }
}
