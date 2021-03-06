package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneMessageService;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.json.JSONObject;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.*;

public class TokenCodeResource {

    private static final Logger logger = Logger.getLogger(TokenCodeResource.class);
    protected final KeycloakSession session;
    protected final TokenCodeType tokenCodeType;
    private final AuthenticationManager.AuthResult auth;

    TokenCodeResource(KeycloakSession session, TokenCodeType tokenCodeType) {
        this.session = session;
        this.tokenCodeType = tokenCodeType;
        this.auth = new AppAuthManager().authenticateIdentityCookie(session, session.getContext().getRealm());
    }

    @POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response getTokenCodeJson(String reqBody) {
        JSONObject jsonObject = new JSONObject(reqBody);
        MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
        for(String key : jsonObject.keySet()){
            formData.addAll(key, jsonObject.getString(key));
        }
        return this.getTokenCode(formData);
    }

    @POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response getTokenCode(MultivaluedMap<String, String> formData) {
        String phoneNumber = formData.getFirst("phone_number");
        String response;

        if (phoneNumber == null){
            response = "{\"status\":0,\"error\":\"phone-number-empty\",\"message\":\"Must inform a phone number.\"}";
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        }
        if (!this.session.getProvider(CaptchaService.class).verify(formData, this.auth) &&
                !this.isTrustedClient(formData.getFirst("client_id"), formData.getFirst("client_secret"))){
            response = "{\"status\":0,\"error\":\"captcha-not-completed\",\"message\":\"Captcha not completed.\"}";
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        }

        if (tokenCodeType != TokenCodeType.REGISTRATION && tokenCodeType != TokenCodeType.VERIFY){
            //??????????????????????????????
            UserModel user = UserUtils.findUserByPhone(session.users(), session.getContext().getRealm(), phoneNumber);
            if(user == null){
                response = "{\"status\":0,\"error\":\"user-not-exists\",\"message\":\"User not exists.\"}";
                return Response.ok(response, APPLICATION_JSON_TYPE).build();
            }
        }

        logger.info(String.format("Requested %s code to %s",tokenCodeType.getLabel(), phoneNumber));
        MessageSendResult result = session.getProvider(PhoneMessageService.class).sendTokenCode(phoneNumber, tokenCodeType);

        response = String.format("{\"status\":1,\"expires_in\":%d,\"resend_expires\":%d}",
                result.getExpiresTime(), result.getResendExpiresTime());

        return Response.ok(response, APPLICATION_JSON_TYPE).build();
    }

    @POST
    @NoCache
    @Path("/resend-expires")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response getResendExpireJson(String reqBody) {
        JSONObject jsonObject = new JSONObject(reqBody);
        return this.getResendExpire(jsonObject.getString("phone_number"));
    }

    @POST
    @NoCache
    @Path("/resend-expires")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response getResendExpirePost(@FormParam("phone_number") String phoneNumber) {
        return this.getResendExpire(phoneNumber);
    }

    @GET
    @NoCache
    @Path("/resend-expires")
    @Produces(APPLICATION_JSON)
    public Response getResendExpire(@QueryParam("phone_number") String phoneNumber) {
        String response;
        if (phoneNumber == null){
            response = "{\"status\":0,\"error\":\"phone-number-empty\",\"message\":\"Must inform a phone number.\"}";
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        }

        TokenCodeService tokenCodeService = session.getProvider(TokenCodeService.class);
        try {
            Date resendExpireDate = tokenCodeService.getResendExpires(phoneNumber, tokenCodeType);
            long resendExpire = resendExpireDate.getTime();
            response = String.format("{\"status\":1,\"resend_expire\":%d}", resendExpire);
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        } catch(BadRequestException e){
            response = String.format("{\"status\":-1,\"message\":\"%s\"}", e.getMessage());
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        }
    }

    private boolean isTrustedClient(String id, String secret){
        if(id == null || secret == null) return false;
        ClientModel client = this.session.getContext().getRealm().getClientByClientId(id);
        return client != null && client.validateSecret(secret);
    }
}
