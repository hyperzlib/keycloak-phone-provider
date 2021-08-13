package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;
import cc.coopersoft.keycloak.phone.utils.RegexUtils;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.annotations.jaxrs.HeaderParam;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

public class GoogleRecaptchaResource {
    private static final Logger log = Logger.getLogger(GoogleRecaptchaResource.class);

    private final KeycloakSession session;
    private AuthenticationManager.AuthResult auth;

    public GoogleRecaptchaResource(KeycloakSession session) {
        this.session = session;
        this.auth = new AppAuthManager().authenticateIdentityCookie(session, session.getContext().getRealm());
        if(this.auth == null){
            this.auth = new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
        }
    }

    private void setCrosHeader(Response.ResponseBuilder response,
                               final String requestMethod,
                               final String requestHeaders, final String origin){
        ClientModel client = this.session.getContext().getClient();
        if(client != null) {
            Set<String> allowedOrigins = client.getWebOrigins();
            for (String allowedOrigin : allowedOrigins) {
                if (RegexUtils.matchGlob(origin, allowedOrigin)) { //当前origin符合要求
                    if (requestHeaders != null)
                        response.header("Access-Control-Allow-Headers", requestHeaders);
                    if (requestMethod != null)
                        response.header("Access-Control-Allow-Methods", requestMethod);
                    response.header("Access-Control-Allow-Origin", allowedOrigin);
                    break;
                }
            }
        }
    }

    @GET
    @Path("code")
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVerificationCodes( @HeaderParam("Access-Control-Request-Method") final String requestMethod,
                                          @HeaderParam("Access-Control-Request-Headers") final String requestHeaders,
                                          @HeaderParam("Origin") final String origin ) {
        CaptchaService captcha = this.session.getProvider(CaptchaService.class);

        String geetestCode = captcha.getFrontendKey(this.auth);
        Response.ResponseBuilder response = Response.status(Response.Status.OK);
        this.setCrosHeader(response, requestMethod, requestHeaders, origin);
        return Response.status(Response.Status.OK).entity(geetestCode).build();
    }

    @OPTIONS
    @Path("code")
    public Response getVerificationCodesCros(
            @HeaderParam("Access-Control-Request-Method") final String requestMethod,
            @HeaderParam("Access-Control-Request-Headers") final String requestHeaders,
            @HeaderParam("Origin") final String origin ) {
        final Response.ResponseBuilder response = Response.ok();
        this.setCrosHeader(response, requestMethod, requestHeaders, origin);
        return response.build();
    }
}
