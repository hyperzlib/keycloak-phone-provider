package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.json.JSONObject;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class VerificationCodeResource {

    private static final Logger logger = Logger.getLogger(VerificationCodeResource.class);
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
        JSONObject jsonObject = new JSONObject(reqBody);
        return this.setUserPhoneNumber(jsonObject.getString("phone_number"), jsonObject.getString("code"));
    }

    @POST
    @NoCache
    @Path("")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response setUserPhoneNumber(@FormParam("phone_number") String phoneNumber,
                                       @FormParam("code") String code){
        try {
            if (auth == null) throw new NotAuthorizedException("Bearer");
            if (phoneNumber == null) throw new BadRequestException("Must inform a phone number");
            if (code == null) throw new BadRequestException("Must inform a token code");

            UserModel user = auth.getUser();
            getTokenCodeService().setUserPhoneNumberByCode(user, phoneNumber, code);

            return Response.ok().entity("{\"status\":1}").build();
        } catch(BadRequestException | NotAuthorizedException e){
            JSONObject response = new JSONObject();
            response.put("status", -1);
            response.put("error", e.getMessage());
            return Response.ok().entity(response.toString()).build();
        }
    }

    @GET
    @POST
    @NoCache
    @Path("/unset")
    @Produces(APPLICATION_JSON)
    @Consumes({APPLICATION_JSON, APPLICATION_FORM_URLENCODED})
    public Response unsetUserPhoneNumber(){
        try {
            if (auth == null) throw new NotAuthorizedException("Bearer");

            UserModel user = auth.getUser();
            if(!user.isEmailVerified()){
                return Response.ok().entity("{\"status\":-2, \"error\": \"Email Unverified.\"}").build();
            } else {
                user.removeAttribute("phoneNumber");
                return Response.ok().entity("{\"status\":1}").build();
            }
        } catch(BadRequestException | NotAuthorizedException e){
            JSONObject response = new JSONObject();
            response.put("status", -1);
            response.put("error", e.getMessage());
            return Response.ok().entity(response.toString()).build();
        }
    }
}