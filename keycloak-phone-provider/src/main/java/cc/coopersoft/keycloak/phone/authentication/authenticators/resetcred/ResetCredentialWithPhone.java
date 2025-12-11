package cc.coopersoft.keycloak.phone.authentication.authenticators.resetcred;

import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import cc.coopersoft.keycloak.phone.providers.constants.PhoneProviderMessages;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialChooseUser;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.DefaultActionTokenKey;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import com.google.auto.service.AutoService;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.*;

@AutoService(AuthenticatorFactory.class)
public class ResetCredentialWithPhone extends ResetCredentialChooseUser {

    private static final Logger logger = Logger.getLogger(ResetCredentialWithPhone.class);

    public static final String PROVIDER_ID = "reset-credentials-with-phone";

    public static final String FIELD_CODE_TYPE = "verificationCodeKind";
    public static final String FIELD_VALIDATION_TYPE = "validationType";

    public static final String VERIFICATION_CODE_KIND = "reset-credential";

    public static final String SHOULD_SEND_EMAIL = "should-send-email";

    public static final String PHONE_RESET_CREDENTIAL_TPL = "login-reset-password-phone-or-email.ftl";

    protected PhoneNumber getPhoneNumber(AuthenticationFlowContext context){
        return new PhoneNumber(context.getHttpRequest().getDecodedFormParameters());
    }

    private Response createResetCredentialForm(LoginFormsProvider form, MultivaluedMap<String, String> formData) {
        Map<String, String> formDataMap = new HashMap<>();
        if (formData != null && !formData.isEmpty()) {
            form.setFormData(formData);

            for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
                formDataMap.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        form.setAttribute("form", formDataMap);
        return form.createForm(PHONE_RESET_CREDENTIAL_TPL);
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        super.authenticate(context);

        String existingUserId = context.getAuthenticationSession().getAuthNote(AbstractIdpAuthenticator.EXISTING_USER_INFO);
        if (existingUserId != null) {
            UserModel existingUser = AbstractIdpAuthenticator.getExistingUser(context.getSession(), context.getRealm(), context.getAuthenticationSession());

            logger.debugf("Forget-password triggered when reauthenticating user after first broker login. Prefilling reset-credential-choose-user screen with user '%s' ", existingUser.getUsername());
            context.setUser(existingUser);
            MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
            LoginFormsProvider form = context.form()
                    .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
            Response challenge = createResetCredentialForm(form, null);
            context.challenge(challenge);
            return;
        }

        String actionTokenUserId = context.getAuthenticationSession().getAuthNote(DefaultActionTokenKey.ACTION_TOKEN_USER_ID);
        if (actionTokenUserId != null) {
            UserModel existingUser = context.getSession().users().getUserById(context.getRealm(), actionTokenUserId);

            // Action token logics handles checks for user ID validity and user being enabled

            logger.debugf("Forget-password triggered when reauthenticating user after authentication via action token. Skipping reset-credential-choose-user screen and using user '%s' ", existingUser.getUsername());
            context.setUser(existingUser);
            context.success();
            return;
        }
        
        LoginFormsProvider form = context.form()
                .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
        Response challenge = createResetCredentialForm(form, null);

        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        EventBuilder event = context.getEvent();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        MultivaluedMap<String, String> resFormData = new MultivaluedHashMap<>();
        
        boolean isPhone = Objects.equals(formData.getFirst(FIELD_VALIDATION_TYPE), "phone");
        logger.infof("Reset credential action, isPhone: %s", isPhone);
        
        String username = formData.getFirst("username");
        PhoneNumber phoneNumber = new PhoneNumber(formData);

        UserModel user = null;
        if (isPhone) {
            if (phoneNumber.isEmpty()) {
                event.error(Errors.USERNAME_MISSING);
                resFormData.add(FIELD_VALIDATION_TYPE, "phone");
                LoginFormsProvider form = context.form()
                        .addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneProviderMessages.MISSING_PHONE_NUMBER))
                        .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
                Response challenge = createResetCredentialForm(form, resFormData);
                context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
                return;
            }

            // 通过手机号重置密码，并验证验证码
            user = UserUtils.findUserByPhone(context.getSession(), context.getRealm(), phoneNumber)
                    .orElse(null);
            if (user == null) {
                // 用户不存在
                event.error(Errors.USER_NOT_FOUND);

                resFormData.add(FIELD_VALIDATION_TYPE, "phone");
                resFormData.add(PhoneConstants.FIELD_AREA_CODE, phoneNumber.areaCode);
                resFormData.add(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.phoneNumber);

                LoginFormsProvider form = context.form()
                        .addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneProviderMessages.PHONE_USER_NOT_FOUND))
                        .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
                Response challenge = createResetCredentialForm(form, resFormData);
                context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
                return;
            } else if (!validateVerificationCode(context, user)) {
                // 验证码错误
                event.error(Errors.INVALID_CODE);
                resFormData.add(FIELD_VALIDATION_TYPE, "phone");
                resFormData.add(PhoneConstants.FIELD_AREA_CODE, phoneNumber.areaCode);
                resFormData.add(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.phoneNumber);
                LoginFormsProvider form = context.form()
                        .addError(new FormMessage(PhoneConstants.FIELD_VERIFICATION_CODE, PhoneProviderMessages.INVALID_SMS_VERIFICATION_CODE))
                        .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
                Response challenge = createResetCredentialForm(form, resFormData);
                context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
                return;
            }
            context.getAuthenticationSession().setAuthNote(SHOULD_SEND_EMAIL, "false");
        } else {
            if (Validation.isBlank(username)) {
                event.error(Errors.USERNAME_MISSING);
                resFormData.add(FIELD_VALIDATION_TYPE, "email");
                LoginFormsProvider form = context.form()
                        .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.MISSING_USERNAME))
                        .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
                Response challenge = createResetCredentialForm(form, resFormData);
                context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
                return;
            }

            // 通过用户名重置密码，仅查找用户
            // 需要配置 Send Reset Email If Not Phone 进行邮件发送
            user = getUserByUsername(context, username);
            if (user == null) {
                event.error(Errors.USER_NOT_FOUND);
                resFormData.add(FIELD_VALIDATION_TYPE, "email");
                resFormData.add("username", username);
                LoginFormsProvider form = context.form()
                        .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.INVALID_USER))
                        .setFormData(resFormData)
                        .setAttribute(FIELD_CODE_TYPE, VERIFICATION_CODE_KIND);
                Response challenge = createResetCredentialForm(form, resFormData);
                context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
                return;
            }

            context.getAuthenticationSession().setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, username);
            context.getAuthenticationSession().setAuthNote(SHOULD_SEND_EMAIL, "true");
        }

        // we don't want people guessing usernames, so if there is a problem, just continue, but don't set the user
        // a null user will notify further executions, that this was a failure.
        if (!user.isEnabled()) {
            event.clone()
                .detail(Details.USERNAME, username)
                .user(user)
                .error(Errors.USER_DISABLED);
            context.clearUser();
        } else {
            context.setUser(user);
        }

        context.success();
    }

    protected UserModel getUserByUsername(AuthenticationFlowContext context, String username) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getSession().users().getUserByUsername(realm, username);
        if (user == null && realm.isLoginWithEmailAllowed() && username.contains("@")) {
            user = context.getSession().users().getUserByEmail(realm, username);
        }
        context.getAuthenticationSession().setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, username);
        return user;
    }

    protected boolean validateVerificationCode(AuthenticationFlowContext context, UserModel user) {
        PhoneNumber phoneNumber = getPhoneNumber(context);
        String code = context.getHttpRequest().getDecodedFormParameters()
                .getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
        return context.getSession().getProvider(TokenCodeService.class)
                .validateCode(user, phoneNumber, code, TokenCodeType.RESET_CREDENTIAL);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Reset Credential With Phone or Email";
    }

    @Override
    public String getHelpText() {
        return "Reset user credential with phone verification code or send reset email.";
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return this;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

//    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();
//
//    static {
//        ProviderConfigProperty property;
//        property = new ProviderConfigProperty();
//        property.setName(RECAPTCHA_SITE_KEY);
//        property.setLabel("recaptcha site key");
//        property.setType(ProviderConfigProperty.STRING_TYPE);
//        property.setHelpText("recaptcha site key");
//        configProperties.add(property);
//
//        property = new ProviderConfigProperty();
//        property.setName(RECAPTCHA_SECRET);
//        property.setLabel("recaptcha secret");
//        property.setType(ProviderConfigProperty.STRING_TYPE);
//        property.setHelpText("recaptcha secret");
//        configProperties.add(property);
//    }
//
//    @Override
//    public List<ProviderConfigProperty> getConfigProperties() {
//        return configProperties;
//    }

}