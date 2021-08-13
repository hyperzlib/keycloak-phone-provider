package cc.coopersoft.keycloak.phone.providers.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;

import java.util.HashMap;

public class AreaCodeService implements Provider {
    public final KeycloakSession session;
    public AreaCodeService(KeycloakSession session){
        this.session = session;
    }

    @Override
    public void close() {

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class AreaCodeData {
        public int areaCode;
        public String countryCode;
        public HashMap<String, String> countryName;
    }
}
