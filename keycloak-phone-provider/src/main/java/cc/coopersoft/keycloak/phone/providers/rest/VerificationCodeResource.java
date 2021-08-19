package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.spi.ConfigService;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.JsonUtils;
import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.util.HashMap;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class VerificationCodeResource {

    private static final Logger logger = Logger.getLogger(VerificationCodeResource.class);

    public static final String ENTITY_SUCCESS = "{\"status\":1}";

    private final KeycloakSession session;
    private final AuthResult auth;

    VerificationCodeResource(KeycloakSession session) {
        this.session = session;
        this.auth = new AppAuthManager().authenticateIdentityCookie(session, session.getContext().getRealm());
    }

    private TokenCodeService getTokenCodeService() {
        return session.getProvider(TokenCodeService.class);
    }

    /*@POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    public Response checkVerificationCode(@QueryParam("phone_number") String phoneNumber,
                                          @QueryParam("code") String code) {

        if (auth == null) throw new NotAuthorizedException("Bearer");
        if (phoneNumber == null) throw new BadRequestException("Must inform a phone number");
        if (code == null) throw new BadRequestException("Must inform a token code");

        UserModel user = auth.getUser();
        getTokenCodeService().validateCode(user, phoneNumber, code);

        return Response.noContent().build();
    }*/

    @POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response setUserPhoneNumberJson(String reqBody){
        try {
            JsonNode jsonObject = JsonLoader.fromString(reqBody);

            return this.setUserPhoneNumber(jsonObject.get(PhoneConstants.FIELD_AREA_CODE).asText(),
                    jsonObject.get(PhoneConstants.FIELD_PHONE_NUMBER).asText(),
                    jsonObject.get(PhoneConstants.FIELD_VERIFICATION_CODE).asText());
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
    public Response setUserPhoneNumber(@FormParam(PhoneConstants.FIELD_AREA_CODE) String areaCode,
                                       @FormParam(PhoneConstants.FIELD_PHONE_NUMBER) String phoneNumberStr,
                                       @FormParam(PhoneConstants.FIELD_VERIFICATION_CODE) String code){
        try {
            PhoneNumber phoneNumber = new PhoneNumber(areaCode, phoneNumberStr);
            if (auth == null) throw new NotAuthorizedException("Bearer");
            if (phoneNumber.isEmpty()) throw new BadRequestException("Must inform a phone number");
            if (code == null) throw new BadRequestException("Must inform a token code");

            UserModel user = auth.getUser();
            getTokenCodeService().setUserPhoneNumberByCode(user, phoneNumber, code);

            return Response.ok().entity(ENTITY_SUCCESS).build();
        } catch(BadRequestException | NotAuthorizedException e){
            HashMap<String, Object> response = new HashMap<>();
            response.put("status", -1);
            response.put("error", e.getMessage());
            response.put("errormsg", "needAuth");
            try {
                return Response.ok().entity(JsonUtils.getInstance().encode(response)).build();
            } catch (JsonProcessingException jsonProcessingException) {
                logger.error("Serialize JSON Error", jsonProcessingException);
            }
            return Response.serverError().build();
        }
    }

    @POST
    @NoCache
    @Path("/unset")
    @Produces(APPLICATION_JSON)
    @Consumes({APPLICATION_JSON, APPLICATION_FORM_URLENCODED})
    public Response unsetUserPhoneNumber(){
        try {
            HashMap<String, Object> response = new HashMap<>();
            ConfigService config = session.getProvider(ConfigService.class);
            if (!config.isAllowUnset()){
                response.put("status", -3);
                response.put("error", "Not allowed to unset phone number");
                response.put("errormsg", "unsetPhoneNumberNotAllowed");
                return Response.ok().entity(JsonUtils.getInstance().encode(response)).build();
            }
            try {
                if (auth == null) throw new NotAuthorizedException("Bearer");

                UserModel user = auth.getUser();
                if (!user.isEmailVerified()) {
                    response.put("status", -2);
                    response.put("error", "Email Unverified.");
                    response.put("errormsg", "needVerifiedEmail");
                    return Response.ok().entity(JsonUtils.getInstance().encode(response)).build();
                } else {
                    user.removeAttribute("phoneNumber");
                    return Response.ok().entity(ENTITY_SUCCESS).build();
                }
            } catch (BadRequestException | NotAuthorizedException e) {
                response.put("status", -1);
                response.put("error", e.getMessage());
                response.put("errormsg", "needAuth");
                return Response.ok().entity(JsonUtils.getInstance().encode(response)).build();
            }
        } catch (JsonProcessingException jsonProcessingException){
            logger.error("Serialize JSON Error", jsonProcessingException);
            return Response.serverError().build();
        }
    }
}