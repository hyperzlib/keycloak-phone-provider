package cc.coopersoft.keycloak.phone.authentication.authenticators.browser;

import cc.coopersoft.keycloak.phone.authentication.forms.SupportPhonePages;
import cc.coopersoft.keycloak.phone.authentication.requiredactions.ConfigSmsOtpRequiredAction;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialModel;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialProvider;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialProviderFactory;
import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneMessageService;
import cc.coopersoft.keycloak.phone.utils.ConfigUtils;
import cc.coopersoft.keycloak.phone.utils.OptionalUtils;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import jakarta.ws.rs.core.NewCookie;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.CredentialValidator;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.http.HttpResponse;
import org.keycloak.models.*;
import org.keycloak.services.validation.Validation;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Optional;

import static cc.coopersoft.keycloak.phone.authentication.authenticators.browser.PhoneOrPasswordLoginForm.VERIFIED_PHONE_NUMBER;
import static cc.coopersoft.keycloak.phone.authentication.forms.SupportPhonePages.ATTRIBUTE_SUPPORT_PHONE;

public class SmsOtpMfaAuthenticator implements Authenticator, CredentialValidator<PhoneOtpCredentialProvider> {

    private static final Logger logger = Logger.getLogger(SmsOtpMfaAuthenticator.class);

    private static final String PAGE = "login-sms-otp.ftl";

    protected boolean validateCookie(AuthenticationFlowContext context) {
        if (ConfigUtils.getOtpExpires(context.getSession()) <= 0)
            return false;

        var invalid = PhoneOtpCredentialModel.getSmsOtpCredentialData(context.getUser())
                .map(PhoneOtpCredentialModel.SmsOtpCredentialData::isSecretInvalid)
                .orElse(true);

        if (invalid)
            return false;

        return Optional.of(context.getHttpRequest().getHttpHeaders().getCookies())
                .flatMap(cookies ->
                        Optional.ofNullable(cookies.get("SMS_OTP_ANSWERED"))
                                .flatMap(cookie -> OptionalUtils.ofBlank(cookie.getValue()))
                                .flatMap(credentialId ->
                                        Optional.ofNullable(cookies.get(credentialId))
                                                .flatMap(cookie -> OptionalUtils.ofBlank(cookie.getValue()))
                                                .map(secret ->  context.getUser()
                                                        .credentialManager()
                                                        .isValid(new UserCredentialModel(credentialId, getType(context.getSession()), secret)))
                                )
                ).orElse(false);
    }

    protected void setCookie(AuthenticationFlowContext context, String credentialId, String secret) {
        int maxCookieAge = ConfigUtils.getOtpExpires(context.getSession());

        if (maxCookieAge <= 0 ){
            return;
        }

        URI uri = context.getUriInfo()
                .getBaseUriBuilder()
                .path("realms")
                .path(context.getRealm().getName())
                .build();

        addCookie(context, "SMS_OTP_ANSWERED", credentialId,
                uri.getRawPath(),
                null, null,
                maxCookieAge,
                false, true);
        addCookie(context, credentialId, secret,
                uri.getRawPath(),
                null, null,
                maxCookieAge,
                false, true);
    }

    public void addCookie(AuthenticationFlowContext context, String name, String value, String path, String domain, String comment, int maxAge, boolean secure, boolean httpOnly) {
        HttpResponse response = context.getSession().getContext().getHttpResponse();
        NewCookie cookie = new NewCookie.Builder(name)
                .version(1)
                .value(value)
                .path(path)
                .maxAge(maxAge)
                .httpOnly(httpOnly)
                .comment(comment)
                .sameSite(NewCookie.SameSite.LAX)
                .secure(secure)
                .build();
        response.setCookieIfAbsent(cookie);
    }

    @Override
    public PhoneOtpCredentialProvider getCredentialProvider(KeycloakSession session) {
        return (PhoneOtpCredentialProvider) session.getProvider(CredentialProvider.class, PhoneOtpCredentialProviderFactory.PROVIDER_ID);
    }

    private String getCredentialPhoneNumber(UserModel user){
        return PhoneOtpCredentialModel.getSmsOtpCredentialData(user)
                .map(PhoneOtpCredentialModel.SmsOtpCredentialData::getPhoneNumber)
                .orElseThrow(() -> new IllegalStateException("Not have OTP Credential"));
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (validateCookie(context)) {
            context.success();
            return;
        }

        String phoneNumberStr = getCredentialPhoneNumber(context.getUser());

        boolean verified = OptionalUtils.ofBlank(context.getAuthenticationSession().getAuthNote(VERIFIED_PHONE_NUMBER))
                .map(number -> number.equalsIgnoreCase(phoneNumberStr))
                .orElse(false);
        if (verified) {
            context.success();
            return;
        }

        PhoneNumber phoneNumber = new PhoneNumber(phoneNumberStr);
        if (!phoneNumber.isValid()) {
            logger.warnf("otp send code invalid phone number: %s", phoneNumberStr);
            context.form().setError(SupportPhonePages.Errors.INVALID_PHONE_NUMBER.message());
            Response challenge = challenge(context,phoneNumberStr);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }

        PhoneMessageService phoneProvider = context.getSession().getProvider(PhoneMessageService.class);
        try {
            MessageSendResult result = phoneProvider.sendTokenCode(
                    phoneNumber,
                    context.getConnection().getRemoteAddr(),
                    TokenCodeType.OTP,
                    null);
            if (!result.ok()) {
                logger.warnf("otp send code failed phone number: %s, reason: %s", phoneNumberStr, result.getErrorMessage());
                context.form().setError(SupportPhonePages.Errors.FAIL.message());

                Response challenge = challenge(context,phoneNumberStr);
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                return;
            }
            context.form()
                    .setInfo("codeSent", phoneNumberStr)
                    .setAttribute("expires", result.getResendExpiresTime())
                    .setAttribute("initSend",true);
        } catch (ForbiddenException e) {
            logger.warn("otp send code Forbidden Exception!", e);
            context.form().setError(SupportPhonePages.Errors.ABUSED.message());
        } catch (Exception e) {
            logger.warn("otp send code Exception!", e);
            context.form().setError(SupportPhonePages.Errors.FAIL.message());
        }

        var credentialData = new PhoneOtpCredentialModel.SmsOtpCredentialData(phoneNumberStr, "", 0);
        PhoneOtpCredentialModel.updateOtpCredential(context.getUser(),credentialData,null);

        Response challenge = challenge(context,phoneNumberStr);
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String secret = formData.getFirst("code");
        String credentialId = formData.getFirst("credentialId");

        String phoneNumber = getCredentialPhoneNumber(context.getUser());

        if (credentialId == null || credentialId.isEmpty()) {
            var defaultOtpCredential = getCredentialProvider(context.getSession())
                    .getDefaultCredential(context.getSession(), context.getRealm(), context.getUser());
            credentialId = defaultOtpCredential==null ? "" : defaultOtpCredential.getId();
        }

        if (Validation.isBlank(secret)){
            context.form()
                    .setError(SupportPhonePages.Errors.NOT_MATCH.message());
            Response challenge = challenge(context,phoneNumber);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
        }

        UserCredentialModel input = new UserCredentialModel(credentialId, getType(context.getSession()), secret);

        boolean validated = getCredentialProvider(context.getSession()).isValid(context.getRealm(), context.getUser(), input);

        if (!validated) {
            context.form()
                    .setError(SupportPhonePages.Errors.NOT_MATCH.message());
            Response challenge = challenge(context,phoneNumber);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }
        setCookie(context,credentialId,secret);
        context.success();
    }

    protected Response challenge(AuthenticationFlowContext context,String phoneNumber) {
        return context.form()
                .setAttribute(ATTRIBUTE_SUPPORT_PHONE, true)
                .setAttribute(SupportPhonePages.ATTEMPTED_PHONE_NUMBER,phoneNumber)
                .createForm(PAGE);
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return getCredentialProvider(session).isConfiguredFor(realm, user, getType(session));
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        user.addRequiredAction(ConfigSmsOtpRequiredAction.PROVIDER_ID);
    }

    @Override
    public void close() {

    }
}
