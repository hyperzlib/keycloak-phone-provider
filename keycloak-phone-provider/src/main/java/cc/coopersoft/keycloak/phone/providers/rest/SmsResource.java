package cc.coopersoft.keycloak.phone.providers.rest;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import org.keycloak.models.KeycloakSession;

import javax.ws.rs.Path;

public class SmsResource {

    private final KeycloakSession session;

    public SmsResource(KeycloakSession session) {
        this.session = session;
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
