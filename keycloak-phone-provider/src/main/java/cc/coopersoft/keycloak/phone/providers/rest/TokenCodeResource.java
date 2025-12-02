package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.AreaCodeService;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneProviderCaptchaService;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneMessageService;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.NoCache;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import java.io.IOException;
import java.util.*;

import static jakarta.ws.rs.core.MediaType.*;

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
    public Response sendTokenCodeJson(String reqBody) {
        try {
            JsonNode jsonObject = JsonLoader.fromString(reqBody);
            MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
            Iterator<String> fieldNames = jsonObject.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                formData.addAll(fieldName, jsonObject.get(fieldName).asText());
            }
            return this.sendTokenCode(formData);
        } catch (IOException e) {
            logger.error(e);
        }
        return Response.serverError().build();
    }

    @POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response sendTokenCode(MultivaluedMap<String, String> formData) {
        PhoneNumber phoneNumber = new PhoneNumber(formData);
        HashMap<String, Object> retData = new HashMap<>();
        ClientConnection clientConnection = session.getContext().getConnection();
        String remoteAddr = clientConnection.getRemoteAddr();

        if (phoneNumber.isEmpty()) {
            retData.put("status", 0);
            retData.put("error", "Must inform a cellphone number.");
            retData.put("errormsg", "phoneNumberCannotBeEmpty");
            return Response.ok(retData, APPLICATION_JSON_TYPE).build();
        }
        // 验证码
        if (!session.getProvider(PhoneProviderCaptchaService.class).verify(formData, this.auth) &&
                !isTrustedClient(formData.getFirst("client_id"), formData.getFirst("client_secret"))) {
            retData.put("status", -1);
            retData.put("error", "Captcha not completed.");
            retData.put("errormsg", "captchaNotCompleted");
            return Response.ok(retData, APPLICATION_JSON_TYPE).build();
        }
        // 区号
        AreaCodeService areaCodeService = session.getProvider(AreaCodeService.class);
        if (!areaCodeService.isAreaCodeAllowed(phoneNumber.getAreaCodeInt())) {
            retData.put("status", -2);
            retData.put("error", "This area is not supported");
            retData.put("errormsg", "areaNotSupported");
            return Response.ok(retData, APPLICATION_JSON_TYPE).build();
        }

        if (tokenCodeType != TokenCodeType.REGISTRATION && tokenCodeType != TokenCodeType.VERIFY) {
            // 需要检测用户是否存在
            UserModel user = UserUtils.findUserByPhone(session, session.getContext().getRealm(), phoneNumber)
                    .orElse(null);
            if (user == null) {
                retData.put("status", 0);
                retData.put("error", "This user not exists");
                retData.put("errormsg", "userNotExists");
                return Response.ok(retData, APPLICATION_JSON_TYPE).build();
            }
        }

        logger.info(String.format("Requested %s code to %s", tokenCodeType.getLabel(), phoneNumber.getFullPhoneNumber()));
        MessageSendResult result = session.getProvider(PhoneMessageService.class)
                .sendTokenCode(phoneNumber, remoteAddr, tokenCodeType, null);

        if (result.ok()) {
            retData.put("status", 1);
            retData.put("expires_in", result.getExpiresTime());
            retData.put("resend_expires", result.getResendExpiresTime());
        } else {
            retData.put("status", 0);
            retData.put("error", result.getErrorMessage());
            retData.put("errormsg", "serverError");
        }
        return Response.ok(retData, APPLICATION_JSON_TYPE).build();
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
        HashMap<String, Object> retData = new HashMap<>();
        PhoneNumber phoneNumber = new PhoneNumber(areaCode, phoneNumberStr);
        if (phoneNumber.isEmpty()) {
            retData.put("status", 0);
            retData.put("error", "Must inform a phone number.");
            retData.put("errormsg", "phoneNumberCannotBeEmpty");
            return Response.ok(retData, APPLICATION_JSON_TYPE).build();
        }

        TokenCodeService tokenCodeService = session.getProvider(TokenCodeService.class);
        try {
            Date resendExpireDate = tokenCodeService.getResendExpires(phoneNumber, tokenCodeType);
            long resendExpire = resendExpireDate.getTime();

            retData.put("status", 1);
            retData.put("resend_expire", resendExpire);
            return Response.ok(retData, APPLICATION_JSON_TYPE).build();
        } catch(BadRequestException e){
            retData.put("status", 0);
            retData.put("error", e.getMessage());
            retData.put("errormsg", "serverError");
            return Response.ok(retData, APPLICATION_JSON_TYPE).build();
        }
    }

    private boolean isTrustedClient(String id, String secret) {
        if(id == null || secret == null) return false;
        ClientModel client = this.session.getContext().getRealm().getClientByClientId(id);
        return client != null && client.validateSecret(secret);
    }
}
