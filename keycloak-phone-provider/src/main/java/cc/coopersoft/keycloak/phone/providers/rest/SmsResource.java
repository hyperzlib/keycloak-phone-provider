package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.ConfigService;
import cc.coopersoft.keycloak.phone.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.util.HashMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

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
            JsonUtils.getInstance().encode(retData);
            return Response.ok(retData).build();
        } catch (JsonProcessingException e) {
            logger.error(e);
        }
        return Response.serverError().build();
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
    public VerificationCodeResource getVerificateCodeResource(){
        return new VerificationCodeResource(session);
    }
}
