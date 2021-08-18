package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.spi.impl.TokenCodeServiceImpl;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

public class AreaCodeService implements Provider {
    private static final Logger logger = Logger.getLogger(AreaCodeService.class);
    public final KeycloakSession session;
    public final ConfigService config;
    public static List<AreaCodeData> areaCodeList;

    public AreaCodeService(KeycloakSession session){
        this.session = session;
        
        this.config = session.getProvider(ConfigService.class);
    }

    public static List<AreaCodeData> getAreaCodeList(String areaCodeConfigPath) throws IOException {
        if(areaCodeList != null) return areaCodeList;
        InputStream fs = new FileInputStream(areaCodeConfigPath);
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JavaType listType = objectMapper.getTypeFactory().constructParametricType(List.class, AreaCodeData.class);
            areaCodeList = objectMapper.readValue(fs, listType);
            fs.close();
            return areaCodeList;
        } catch (IOException ex){
            fs.close();
            throw ex;
        }
    }

    @Override
    public void close() {

    }

    public Stream<AreaCodeData> getStream(){
        try {
            return getAreaCodeList(config.getAreaCodeConfig()).stream();
        } catch (IOException ex){
            logger.error(ex);
        }
        return Collections.EMPTY_LIST.stream();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class AreaCodeData {
        @JsonAlias("areaCode")
        public int areaCode;

        @JsonAlias("countryCode")
        public String countryCode;

        @JsonAlias("name")
        public HashMap<String, String> countryNameMessages;

        @JsonIgnore
        public String getCountryName(String languageCode){
            languageCode = languageCode.toLowerCase(Locale.ROOT);
            if(countryNameMessages.containsKey(languageCode)){
                return countryNameMessages.get(languageCode);
            } else {
                int splitPos = languageCode.indexOf("-");
                if(splitPos > 0) {
                    String baseLanguageCode = languageCode.substring(0, splitPos - 1).toLowerCase(Locale.ROOT);
                    if(countryNameMessages.containsKey(baseLanguageCode)){
                        return countryNameMessages.get(baseLanguageCode);
                    }
                }
                if(countryNameMessages.containsKey("en")){
                    return countryNameMessages.get("en");
                }
            }
            return countryCode;
        }
    }
}
