package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.authentication.requiredactions.util.EmailCooldownManager;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.utils.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.actiontoken.verifyemail.VerifyEmailActionToken;
import org.keycloak.authentication.requiredactions.util.UpdateProfileContext;
import org.keycloak.authentication.requiredactions.util.UserUpdateProfileContext;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.ProfileBean;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.protocol.AuthorizationEndpointBase;
import org.keycloak.services.Urls;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.keycloak.authentication.requiredactions.VerifyEmail.EMAIL_RESEND_COOLDOWN_KEY_PREFIX;
import static org.keycloak.forms.login.LoginFormsProvider.UPDATE_PROFILE_CONTEXT_ATTR;

public class VerifyPhoneOrEmailRequiredAction implements RequiredActionProvider {
    private static final Logger logger = Logger.getLogger(VerifyPhoneOrEmailRequiredAction.class);
    public static final String PROVIDER_ID = "VERIFY_PHONE_OR_EMAIL";
    public static final String VERIFY_PHONE_OR_EMAIL_FORM_FTL = "login-verify-phone-or-email.ftl";
    private final Config.Scope config;

    public VerifyPhoneOrEmailRequiredAction(Config.Scope config) {
        this.config = config;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        if (!context.getUser().isEmailVerified() && !UserUtils.isUserPhoneNumberVerified(context.getUser())) {
            // Don't add VERIFY_PHONE_OR_EMAIL if UPDATE_EMAIL or UPDATE_PHONE_NUMBER is already present
            if (context.getUser().getRequiredActionsStream().noneMatch(action ->
                    UserModel.RequiredAction.UPDATE_EMAIL.name().equals(action) ||
                            UpdatePhoneNumberRequiredAction.PROVIDER_ID.equals(action))) {
                context.getUser().addRequiredAction(PROVIDER_ID);
                logger.debug("User is required to verify phone or email");
            } else {
                logger.debug("Skipping VERIFY_PHONE_OR_EMAIL because UPDATE_EMAIL or UPDATE_PHONE_NUMBER is already present");
            }
        }
    }

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    private Response createForm(RequiredActionContext context, LoginFormsProvider form, MultivaluedMap<String, String> formData) {
        if (formData == null) {
            formData = new MultivaluedHashMap<>();
        }
        Map<String, String> formDataMap = TypeUtils.multivaluedMapToMap(formData);
        form.setAttribute("form", formDataMap);
        UpdateProfileContext userCtx = new UserUpdateProfileContext(context.getRealm(), context.getUser());
        form.setAttribute(UPDATE_PROFILE_CONTEXT_ATTR, userCtx);
        form.setAttribute("user", new ProfileBean(userCtx, formData));
        return form.createForm(VERIFY_PHONE_OR_EMAIL_FORM_FTL);
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        process(context, true);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        process(context, false);
    }

    private void process(RequiredActionContext context, boolean isChallenge) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        UserModel user = context.getUser();

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        MultivaluedMap<String, String> resFormData = new MultivaluedHashMap<>(formData);

        resFormData.remove(PhoneConstants.FIELD_VERIFICATION_CODE);

        boolean isPhone = Objects.equals(formData.getFirst(PhoneConstants.FIELD_CREDENTIAL_TYPE), "phone");

        if (user.isEmailVerified() || UserUtils.isUserPhoneNumberVerified(user)) {
            context.success();
            authSession.removeAuthNote(Constants.VERIFY_EMAIL_KEY);
            return;
        }

        LoginFormsProvider loginFormsProvider = context.form();
        loginFormsProvider.setAuthenticationSession(context.getAuthenticationSession());
        Response challenge;
        authSession.setClientNote(AuthorizationEndpointBase.APP_INITIATED_FLOW, null);

        if (!isChallenge) {
            // User submitted the form
            if (isPhone) {
                // User chose to verify phone instead of email
                PhoneNumber phoneNumber = new PhoneNumber(formData);
                if (!phoneNumber.isValid()) {
                    loginFormsProvider
                            .addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.MISSING_PHONE_NUMBER));
                    context.challenge(createForm(context, loginFormsProvider, resFormData));
                    return;
                }

                context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());

                KeycloakSession session = context.getSession();
                if (!ConfigUtils.isDuplicatePhoneAllowed(session) &&
                        UserUtils.findUserByPhone(session, context.getRealm(), phoneNumber).isPresent()) {
                    formData.remove(PhoneConstants.FIELD_PHONE_NUMBER);
                    context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
                    loginFormsProvider
                            .addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.PHONE_EXISTS));


                    context.challenge(createForm(context, loginFormsProvider, resFormData));
                    return;
                }

                // 检查短信验证码
                String verificationCode = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
                TokenCodeRepresentation tokenCode = ServiceUtils.getTokenCodeService(session)
                        .currentProcess(phoneNumber, TokenCodeType.REGISTRATION);
                if (Validation.isBlank(verificationCode) || tokenCode == null ||
                        !tokenCode.getCode().equals(verificationCode)) {
                    context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
                    loginFormsProvider
                            .addError(new FormMessage(PhoneConstants.FIELD_VERIFICATION_CODE, PhoneConstants.SMS_CODE_MISMATCH));
                    context.challenge(createForm(context, loginFormsProvider, resFormData));
                    return;
                }

                ServiceUtils.getTokenCodeService(session)
                        .tokenValidated(user, phoneNumber, tokenCode.getId(), false);

                context.getEvent().success();
                context.success();
                return;
            } else {
                // Email verification
                logger.debugf("Re-sending email requested for user: %s", user.getUsername());
                String email = formData.getFirst(Validation.FIELD_EMAIL);
                if (email == null) {
                    email = user.getEmail();
                }

                resFormData.putSingle(Validation.FIELD_EMAIL, email);

                if (Objects.equals(authSession.getAuthNote(Constants.VERIFY_EMAIL_KEY), email)) {
                    // Check cooldown when email address not changed
                    Long remaining = EmailCooldownManager.retrieveCooldownEntry(context, EMAIL_RESEND_COOLDOWN_KEY_PREFIX);
                    if (remaining != null) {
                        Response retryPage = context.form()
                                .setError(Messages.COOLDOWN_VERIFICATION_EMAIL, remaining)
                                .createResponse(UserModel.RequiredAction.VERIFY_EMAIL); // re-render same verify email page

                        context.challenge(retryPage);
                        return;
                    }
                }

                if (!email.equals(user.getEmail())) {
                    if (!Validation.isEmailValid(email)) {
                        logger.debugf("Invalid email address provided: %s", email);
                        loginFormsProvider
                                .addError(new FormMessage(Validation.FIELD_EMAIL, Messages.INVALID_EMAIL));
                        context.challenge(createForm(context, loginFormsProvider, resFormData));
                        return;
                    }

                    if (!context.getRealm().isDuplicateEmailsAllowed()) {
                        // Check if email is already used
                        UserModel userByEmail = context.getSession().users()
                                .getUserByEmail(context.getRealm(), email);
                        if (userByEmail != null && !userByEmail.getId().equals(user.getId())) {
                            logger.debugf("Email %s already in use by another user", email);
                            loginFormsProvider
                                    .addError(new FormMessage(Validation.FIELD_EMAIL, Messages.EMAIL_EXISTS));
                            context.challenge(createForm(context, loginFormsProvider, resFormData));
                            return;
                        }
                    }

                    // Update user email
                    String oldEmail = user.getEmail();
                    user.setEmail(email);
                    context.getEvent().event(EventType.UPDATE_EMAIL)
                            .detail(Details.PREVIOUS_EMAIL, oldEmail).detail(Details.UPDATED_EMAIL, email);
                    context.getEvent().detail(Details.EMAIL, email);
                    resFormData.putSingle(Validation.FIELD_EMAIL, email);
                }

                // Adding the cooldown entry first to prevent concurrent operations
                EmailCooldownManager.addCooldownEntry(context, EMAIL_RESEND_COOLDOWN_KEY_PREFIX);
                authSession.setAuthNote(Constants.VERIFY_EMAIL_KEY, email);
                EventBuilder event = context.getEvent().clone().event(EventType.SEND_VERIFY_EMAIL).detail(Details.EMAIL, email);
                context.challenge(sendVerifyEmail(context, event, resFormData));
            }
            return;
        } else {
            // Send email when first challenge
            String email = user.getEmail();
            if (!Validation.isBlank(email) && !Objects.equals(authSession.getAuthNote(Constants.VERIFY_EMAIL_KEY), email)
                    && !isCurrentActionTriggeredFromAIA(context)) {
                resFormData.putSingle(Validation.FIELD_EMAIL, email);

                // Adding the cooldown entry first to prevent concurrent operations
                EmailCooldownManager.addCooldownEntry(context, EMAIL_RESEND_COOLDOWN_KEY_PREFIX);
                authSession.setAuthNote(Constants.VERIFY_EMAIL_KEY, email);
                EventBuilder event = context.getEvent().clone().event(EventType.SEND_VERIFY_EMAIL).detail(Details.EMAIL, email);
                challenge = sendVerifyEmail(context, event, resFormData);
                context.challenge(challenge);
                return;
            }
        }

        String email = user.getEmail();
        if (email != null) {
            resFormData.putSingle(Validation.FIELD_EMAIL, email);
        }
        challenge = createForm(context, loginFormsProvider, resFormData);
        context.challenge(challenge);
    }

    private boolean isCurrentActionTriggeredFromAIA(RequiredActionContext context) {
        return Objects.equals(context.getAuthenticationSession().getClientNote(Constants.KC_ACTION), PROVIDER_ID);
    }

    @Override
    public void close() {

    }

    private Response sendVerifyEmail(RequiredActionContext context,
                                     EventBuilder event,
                                     MultivaluedMap<String, String> resFormData) throws UriBuilderException, IllegalArgumentException {
        RealmModel realm = context.getRealm();
        UriInfo uriInfo = context.getUriInfo();
        UserModel user = context.getUser();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        KeycloakSession session = context.getSession();

        int validityInSecs = realm.getActionTokenGeneratedByUserLifespan(VerifyEmailActionToken.TOKEN_TYPE);
        int absoluteExpirationInSecs = Time.currentTime() + validityInSecs;

        String authSessionEncodedId = AuthenticationSessionCompoundId.fromAuthSession(authSession).getEncodedId();
        VerifyEmailActionToken token = new VerifyEmailActionToken(user.getId(), absoluteExpirationInSecs, authSessionEncodedId, user.getEmail(), authSession.getClient().getClientId());
        UriBuilder builder = Urls.actionTokenBuilder(uriInfo.getBaseUri(), token.serialize(session, realm, uriInfo),
                authSession.getClient().getClientId(), authSession.getTabId(), AuthenticationProcessor.getClientData(session, authSession));
        String link = builder.build(realm.getName()).toString();
        long expirationInMinutes = TimeUnit.SECONDS.toMinutes(validityInSecs);

        try {
            session
                    .getProvider(EmailTemplateProvider.class)
                    .setAuthenticationSession(authSession)
                    .setRealm(realm)
                    .setUser(user)
                    .sendVerifyEmail(link, expirationInMinutes);
            event.success();

            LoginFormsProvider form = context.form()
                    .setAttribute("emailSent", true);

            return createForm(context, form, resFormData);
        } catch (EmailException e) {
            event.clone().event(EventType.SEND_VERIFY_EMAIL)
                    .detail(Details.REASON, e.getMessage())
                    .user(user)
                    .error(Errors.EMAIL_SEND_FAILED);
            logger.error("Failed to send verification email", e);
            context.failure(Messages.EMAIL_SENT_ERROR);
            LoginFormsProvider form = context.form()
                    .setError(Messages.EMAIL_SENT_ERROR);
            return createForm(context, form, resFormData);
        }
    }
}
