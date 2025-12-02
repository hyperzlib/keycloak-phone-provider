package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.AreaCodeService;
import cc.coopersoft.keycloak.phone.providers.spi.ConfigService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.*;

public class SmsResource {

    private static final Logger logger = Logger.getLogger(SmsResource.class);

    private final KeycloakSession session;

    public SmsResource(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("")
    @Produces(APPLICATION_JSON)
    public Response getSMSConfig() {
        ConfigService config = session.getProvider(ConfigService.class);
        HashMap<String, Object> retData = new HashMap<>();
        retData.put("tokenExpires", config.getTokenExpires());
        retData.put("defaultAreaCode", config.getDefaultAreaCode());
        retData.put("areaLocked", config.isAreaLocked());
        retData.put("allowUnset", config.isAllowUnset());
        try {
            AreaCodeService areaCodeService = session.getProvider(AreaCodeService.class);
            List<AreaCodeService.AreaCodeData> areaCodeList = areaCodeService.getAreaCodeList();
            retData.put("areaCodeList", areaCodeList);
        } catch (IOException e){
            logger.error(e);
        }
        return Response.ok(retData, APPLICATION_JSON_TYPE).build();
    }

    @Path("verification-code")
    public TokenCodeResource getVerificationCodeResource() {
        return new TokenCodeResource(session, TokenCodeType.VERIFY);
    }

    @Path("authentication-code")
    public TokenCodeResource getAuthenticationCodeResource() {
        return new TokenCodeResource(session, TokenCodeType.OTP);
    }

    @Path("login-code")
    public TokenCodeResource getLoginCodeResource() {
        return new TokenCodeResource(session, TokenCodeType.LOGIN);
    }

    @Path("registration-code")
    public TokenCodeResource getRegistrationCodeResource() {
        return new TokenCodeResource(session, TokenCodeType.REGISTRATION);
    }

    @Path("reset-code")
    public TokenCodeResource getResetCodeResource() {
        return new TokenCodeResource(session, TokenCodeType.RESET);
    }

    @Path("update-profile")
    public TokenCodeResource getUpdateProfileResource(){
        return new TokenCodeResource(session, TokenCodeType.VERIFY);
    }
}
