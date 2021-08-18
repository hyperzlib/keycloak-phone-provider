package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneMessageService;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
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
import java.util.Date;
import java.util.Iterator;
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
        try {
            JsonNode jsonObject = JsonLoader.fromString(reqBody);
            MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = jsonObject.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> node = it.next();
                formData.addAll(node.getKey(), node.getValue().asText());
            }
            return this.getTokenCode(formData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    @POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response getTokenCode(MultivaluedMap<String, String> formData) {
        PhoneNumber phoneNumber = new PhoneNumber(formData);
        String response;

        if (phoneNumber.isEmpty()){
            response = "{\"status\":0,\"error\":\"phoneNumberEmpty\",\"message\":\"Must inform a phone number.\"}";
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        }
        if (!this.session.getProvider(CaptchaService.class).verify(formData, this.auth) &&
                !this.isTrustedClient(formData.getFirst("client_id"), formData.getFirst("client_secret"))){
            response = "{\"status\":0,\"error\":\"captchaNotCompleted\",\"message\":\"Captcha not completed.\"}";
            return Response.ok(response, APPLICATION_JSON_TYPE).build();
        }

        if (tokenCodeType != TokenCodeType.REGISTRATION && tokenCodeType != TokenCodeType.VERIFY){
            //需要检测用户是否存在
            UserModel user = UserUtils.findUserByPhone(session.users(), session.getContext().getRealm(), phoneNumber);
            if(user == null){
                response = "{\"status\":0,\"error\":\"userNotExists\",\"message\":\"User not exists.\"}";
                return Response.ok(response, APPLICATION_JSON_TYPE).build();
            }
        }

        logger.info(String.format("Requested %s code to %s",tokenCodeType.getLabel(), phoneNumber.getFullPhoneNumber()));
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
        try {
            JsonNode jsonObject = JsonLoader.fromString(reqBody);
            return this.getResendExpire(jsonObject.get(PhoneConstants.FIELD_AREA_CODE).asText(),
                    jsonObject.get(PhoneConstants.FIELD_PHONE_NUMBER).asText());
        } catch (IOException e) {
            logger.error(e);
        }
        return Response.serverError().build();
    }

    @POST
    @NoCache
    @Path("/resend-expires")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response getResendExpirePost(@FormParam(PhoneConstants.FIELD_AREA_CODE) String areaCode,
                                        @FormParam(PhoneConstants.FIELD_PHONE_NUMBER) String phoneNumber) {
        return this.getResendExpire(areaCode, phoneNumber);
    }

    @GET
    @NoCache
    @Path("/resend-expires")
    @Produces(APPLICATION_JSON)
    public Response getResendExpire(@QueryParam(PhoneConstants.FIELD_AREA_CODE) String areaCode,
                                    @QueryParam(PhoneConstants.FIELD_PHONE_NUMBER) String phoneNumberStr) {
        String response;
        PhoneNumber phoneNumber = new PhoneNumber(areaCode, phoneNumberStr);
        if (phoneNumber.isEmpty()){
            response = "{\"status\":0,\"error\":\"phoneNumberEmpty\",\"message\":\"Must inform a phone number.\"}";
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
