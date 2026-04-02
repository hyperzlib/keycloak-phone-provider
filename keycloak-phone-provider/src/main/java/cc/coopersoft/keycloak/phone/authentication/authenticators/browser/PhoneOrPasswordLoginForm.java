package cc.coopersoft.keycloak.phone.authentication.authenticators.browser;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.*;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticatorUtil;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.authentication.authenticators.browser.WebAuthnConditionalUIAuthenticator;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.credential.WebAuthnCredentialModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhoneOrPasswordLoginForm extends AbstractUsernameFormAuthenticator implements Authenticator {
    protected static ServicesLogger log = ServicesLogger.LOGGER;

    public static final String PHONE_LOGIN_FORM_TPL = "login-phone-or-password.ftl";

    public static final String USER_NOT_EXISTS = "userNotExists";

    public static final String VERIFIED_PHONE_NUMBER = "LOGIN_BY_PHONE_VERIFY";

    private Config.Scope config;

    protected final WebAuthnConditionalUIAuthenticator webauthnAuth;

    public PhoneOrPasswordLoginForm() {
        webauthnAuth = null;
    }

    public PhoneOrPasswordLoginForm(KeycloakSession session) {
        webauthnAuth = new WebAuthnConditionalUIAuthenticator(session, (context) -> {
            LoginFormsProvider form = context.form();
            fillFormData(form, context.getHttpRequest().getDecodedFormParameters(), "");
            return createLoginForm(form);
        });
    }

    // Copied from WebAuthnConditionalUIAuthenticator
    protected String webauthnAuth_getCredentialType() {
        return WebAuthnCredentialModel.TYPE_PASSWORDLESS;
    }

    protected void fillFormData(LoginFormsProvider forms, MultivaluedMap<String, String> formData, String defaultLoginMethod) {
        Map<String, String> formDataMap = new HashMap<>();
        if (!formData.isEmpty()) {
            forms.setFormData(formData);

            for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
                if (entry.getKey().equals(PhoneConstants.FIELD_VERIFICATION_CODE) ||
                        entry.getKey().equals(Validation.FIELD_PASSWORD)) {
                    // do not put verification code into form data map
                    continue;
                }
                formDataMap.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        forms.setAttribute("form", formDataMap);
        forms.setAttribute("defaultLoginMethod", defaultLoginMethod);
    }

    protected Response createLoginForm(LoginFormsProvider form) {
        return form.createForm(PHONE_LOGIN_FORM_TPL);
    }

    protected Response challenge(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        final Map<String, String> config = context.getAuthenticatorConfig() != null
                ? context.getAuthenticatorConfig().getConfig()
                : Collections.emptyMap();
        String defaultLoginMethod = config.getOrDefault(PhoneOrPasswordLoginFormFactory.CONF_DEFAULT_LOGIN_METHOD, "username");
        LoginFormsProvider form = context.form();

        fillFormData(form, formData, defaultLoginMethod);

        return createLoginForm(form);
    }

    protected Response challenge(AuthenticationFlowContext context, String error,
                                 MultivaluedMap<String, String> formData) {
        final Map<String, String> config = context.getAuthenticatorConfig() != null
                ? context.getAuthenticatorConfig().getConfig()
                : Collections.emptyMap();
        String defaultLoginMethod = config.getOrDefault(PhoneOrPasswordLoginFormFactory.CONF_DEFAULT_LOGIN_METHOD, "username");
        LoginFormsProvider form = context.form()
                .setExecution(context.getExecution().getId());

        if (error != null) form.setError(error);

        fillFormData(form, formData, defaultLoginMethod);

        return challenge(context, formData);
    }

    @Override
    protected Response challenge(AuthenticationFlowContext context, String error, String field) {
        final Map<String, String> config = context.getAuthenticatorConfig() != null
            ? context.getAuthenticatorConfig().getConfig()
            : Collections.emptyMap();
        String defaultLoginMethod = config.getOrDefault(PhoneOrPasswordLoginFormFactory.CONF_DEFAULT_LOGIN_METHOD, "username");

        if (isConditionalPasskeysEnabled(context.getUser())) {
            // setup webauthn data when possible
            webauthnAuth.fillContextForm(context);
        }

        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        if (error != null) {
            if (field != null) {
                form.addError(new FormMessage(field, error));
            } else {
                form.setError(error, new Object[0]);
            }
        }

        fillFormData(form, context.getHttpRequest().getDecodedFormParameters(), defaultLoginMethod);

        return createLoginForm(form);
    }

    protected boolean validateForm(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        // Check if it's phone login or password login
        if (formData.containsKey(PhoneConstants.FIELD_CREDENTIAL_TYPE) &&
                formData.getFirst(PhoneConstants.FIELD_CREDENTIAL_TYPE).equals("phone")) {
            return validatePhoneLogin(context, formData);
        } else {
            return validateUserAndPassword(context, formData);
        }
    }

    protected boolean validatePhoneLogin(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        KeycloakSession session = context.getSession();

        PhoneNumber phoneNumber = new PhoneNumber(formData);
        if (phoneNumber.isEmpty()) {
            Response challengeResponse = challenge(context, PhoneConstants.MISSING_PHONE_NUMBER, formData);
            context.challenge(challengeResponse);
            return false;
        }

        UserModel user = UserUtils.findUserByPhone(session, context.getRealm(), phoneNumber).orElse(null);
        if (user == null) { //用户不存在
            Response challengeResponse = challenge(context, USER_NOT_EXISTS, formData);
            context.challenge(challengeResponse);
            return false;
        }

        String code = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
        if (Validation.isBlank(code)) {
            Response challengeResponse = challenge(context, PhoneConstants.MISSING_VERIFY_CODE, formData);
            context.challenge(challengeResponse);
            return false;
        }

        TokenCodeService tokenCodeService = ServiceUtils.getTokenCodeService(session);
        if (!tokenCodeService.validateCode(user, phoneNumber, code, TokenCodeType.LOGIN)) { //验证码错误
            Response challengeResponse = challenge(context, PhoneConstants.SMS_CODE_MISMATCH, formData);
            context.challenge(challengeResponse);
            return false;
        }

        return validateUser(context, user, formData);
    }

    private boolean validateUser(AuthenticationFlowContext context, UserModel user,
                                 MultivaluedMap<String, String> inputData) {
        if (!enabledUser(context, user, inputData)) {
            return false;
        }
        String rememberMe = inputData.getFirst("rememberMe");
        boolean remember = rememberMe != null && rememberMe.equalsIgnoreCase("on");
        if (remember) {
            context.getAuthenticationSession().setAuthNote(Details.REMEMBER_ME, "true");
            context.getEvent().detail(Details.REMEMBER_ME, "true");
        } else {
            context.getAuthenticationSession().removeAuthNote(Details.REMEMBER_ME);
        }
        context.setUser(user);
        return true;
    }

    public boolean enabledUser(AuthenticationFlowContext context, UserModel user,
                               MultivaluedMap<String, String> formData) {
        if (!user.isEnabled()) {
            context.getEvent().user(user);
            context.getEvent().error(Errors.USER_DISABLED);
            Response challengeResponse = challenge(context, Messages.ACCOUNT_DISABLED, formData);
            context.challenge(challengeResponse);
            return false;
        }
        return !isTemporarilyDisabledByBruteForce(context, user, formData);
    }

    protected boolean isTemporarilyDisabledByBruteForce(AuthenticationFlowContext context, UserModel user,
                                                        MultivaluedMap<String, String> formData) {
        if (context.getRealm().isBruteForceProtected()) {
            if (context.getProtector().isTemporarilyDisabled(context.getSession(), context.getRealm(), user)) {
                context.getEvent().user(user);
                context.getEvent().error(Errors.USER_TEMPORARILY_DISABLED);
                Response challengeResponse = challenge(context, Messages.INVALID_USER, formData);
                context.challenge(challengeResponse);
                return true;
            }
        }
        return false;
    }

    protected boolean alreadyAuthenticatedUsingPasswordlessCredential(AuthenticationFlowContext context) {
        return alreadyAuthenticatedUsingPasswordlessCredential(context.getAuthenticationSession());
    }

    protected boolean alreadyAuthenticatedUsingPasswordlessCredential(AuthenticationSessionModel authSession) {
        // check if the authentication was already done using passwordless via passkeys
        return webauthnAuth != null && webauthnAuth.isPasskeysEnabled()
                && AuthenticatorUtil.getAuthnCredentials(authSession).contains(webauthnAuth_getCredentialType());
    }


    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        
        if (formData.containsKey("cancel")) {
            context.cancelLogin();
            return;
        } else if (webauthnAuth != null && webauthnAuth.isPasskeysEnabled()
                && (formData.containsKey(WebAuthnConstants.AUTHENTICATOR_DATA) || formData.containsKey(WebAuthnConstants.ERROR))) {
            // webauth form submission, try to action using the webauthn authenticator
            webauthnAuth.action(context);
            return;
        } else if (!validateForm(context, formData)) {
            // normal username and form authenticator or phone authentication
            return;
        }
        
        // Success - determine credential type based on login method
        if (formData.containsKey(PhoneConstants.FIELD_CREDENTIAL_TYPE) &&
                formData.getFirst(PhoneConstants.FIELD_CREDENTIAL_TYPE).equals("phone")) {
            context.success(); // Phone authentication doesn't specify credential type
        } else {
            context.success(PasswordCredentialModel.TYPE);
        }
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        String loginHint = context.getAuthenticationSession().getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);

        String rememberMeUsername = AuthenticationManager.getRememberMeUsername(context.getSession());

        if (context.getUser() != null) {
            if (alreadyAuthenticatedUsingPasswordlessCredential(context)) {
                // if already authenticated using passwordless webauthn just success
                context.success();
                return;
            }

            LoginFormsProvider form = context.form();
            form.setAttribute(LoginFormsProvider.USERNAME_HIDDEN, true);
            form.setAttribute(LoginFormsProvider.REGISTRATION_DISABLED, true);

            String phoneNumberStr = context.getUser().getFirstAttribute(PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER);
            if (!Validation.isBlank(phoneNumberStr)) {
                PhoneNumber phoneNumber = new PhoneNumber(phoneNumberStr);
                formData.putSingle(PhoneConstants.FIELD_AREA_CODE, phoneNumber.getAreaCode());
                formData.putSingle(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getPhoneNumber());
            } else {
                form.setAttribute("phoneNumberNotBound", true);
            }

            context.getAuthenticationSession().setAuthNote(USER_SET_BEFORE_USERNAME_PASSWORD_AUTH, "true");
        } else {
            context.getAuthenticationSession().removeAuthNote(USER_SET_BEFORE_USERNAME_PASSWORD_AUTH);
            if (loginHint != null || rememberMeUsername != null) {
                if (loginHint != null) {
                    formData.add(AuthenticationManager.FORM_USERNAME, loginHint);
                } else {
                    formData.add(AuthenticationManager.FORM_USERNAME, rememberMeUsername);
                    formData.add("rememberMe", "on");
                }
            }
        }
        
        // setup webauthn data when passkeys enabled
        if (isConditionalPasskeysEnabled(context.getUser())) {
            webauthnAuth.fillContextForm(context);
        }
        
        Response challengeResponse = challenge(context, formData);
        context.challenge(challengeResponse);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // never called
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // never called
    }

    @Override
    public void close() {

    }

    protected boolean isConditionalPasskeysEnabled(UserModel currentUser) {
        return webauthnAuth != null && webauthnAuth.isPasskeysEnabled() &&
                (currentUser == null || currentUser.credentialManager().isConfiguredFor(webauthnAuth_getCredentialType()));
    }
}
