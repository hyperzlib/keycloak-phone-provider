package cc.coopersoft.keycloak.phone.providers.spi;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.provider.Provider;

import java.util.*;

@Getter
public class ConfigService implements Provider {
    private static final Logger logger = Logger.getLogger(ConfigService.class);

    public final int tokenExpires;
    public final int defaultAreaCode;
    public final String areaCodeConfig;
    public final boolean areaLocked;
    public final boolean allowUnset;
    public final boolean duplicatePhoneAllowed;
    public final String senderForAreaCodesJson;
    public Map<String, int[]> senderForAreaCodes = null;
    public Map<Integer, String> areaCodeSenderMap = null;
    public final String defaultSender;

    public ConfigService(Config.Scope config) {
        this.tokenExpires = config.getInt("tokenExpires", 300);
        this.defaultAreaCode = config.getInt("defaultAreacode", 86);
        this.areaCodeConfig = config.get("areacodeConfig", "./areacode.json");
        this.areaLocked = config.getBoolean("areaLocked", false);
        this.allowUnset = config.getBoolean("allowUnset", true);
        this.duplicatePhoneAllowed = config.getBoolean("duplicatePhoneAllowed", false);
        this.defaultSender = config.get("defaultSender", "");
        this.senderForAreaCodesJson = config.get("senderForAreaCodes", "{}");
    }

    private void readSenderForAreaCodes() {
        if (this.senderForAreaCodes == null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JavaType mapType = mapper.getTypeFactory().constructParametricType(Map.class, String.class, int[].class);
                this.senderForAreaCodes = mapper.readValue(this.senderForAreaCodesJson, mapType);
            } catch (Exception ex) {
                logger.error("Failed to parse senderForAreaCodes configuration, using empty map.", ex);
            }
        }

        if (this.areaCodeSenderMap == null) {
            // Create areaCodeSenderMap from senderForAreaCodes
            this.areaCodeSenderMap = new HashMap<>();
            for (Map.Entry<String, int[]> entry : this.senderForAreaCodes.entrySet()) {
                String sender = entry.getKey();
                int[] areaCodes = entry.getValue();
                for (int areaCode : areaCodes) {
                    this.areaCodeSenderMap.put(areaCode, sender);
                }
            }
        }
    }

    public Map<String, int[]> getSenderForAreaCodes() {
        readSenderForAreaCodes();
        return this.senderForAreaCodes;
    }

    public Map<Integer, String> getAreaCodeSenderMap() {
        readSenderForAreaCodes();
        return this.areaCodeSenderMap;
    }

    @Override
    public void close() {

    }
}
