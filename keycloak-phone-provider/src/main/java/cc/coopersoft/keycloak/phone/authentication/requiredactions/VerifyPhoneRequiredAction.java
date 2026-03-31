package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.utils.*;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.requiredactions.util.UpdateProfileContext;
import org.keycloak.authentication.requiredactions.util.UserUpdateProfileContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.ProfileBean;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;

import java.util.Map;

import static org.keycloak.forms.login.LoginFormsProvider.UPDATE_PROFILE_CONTEXT_ATTR;

public class VerifyPhoneRequiredAction implements RequiredActionProvider {

    private static final Logger logger = Logger.getLogger(VerifyPhoneRequiredAction.class);

    public static final String PROVIDER_ID = "VERIFY_PHONE_NUMBER";
    public static final String VERIFY_PHONE_FORM_FTL = "login-verify-phone.ftl";

    private final Config.Scope config;

    public VerifyPhoneRequiredAction(Config.Scope config) {
        this.config = config;
    }

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        if (!UserUtils.isUserPhoneNumberVerified(context.getUser())) {
            // Don't add VERIFY_PHONE_NUMBER if UPDATE_PHONE_NUMBER is already pending
            if (context.getUser().getRequiredActionsStream()
                    .noneMatch(UpdatePhoneNumberRequiredAction.PROVIDER_ID::equals)) {
                context.getUser().addRequiredAction(PROVIDER_ID);
                logger.debug("User is required to verify phone number");
            } else {
                logger.debug("Skipping VERIFY_PHONE_NUMBER because UPDATE_PHONE_NUMBER is already present");
            }
        }
    }

    private Response createForm(RequiredActionContext context,
                                LoginFormsProvider form,
                                MultivaluedMap<String, String> formData) {
        if (formData == null) {
            formData = new MultivaluedHashMap<>();
        }
        Map<String, String> formDataMap = TypeUtils.multivaluedMapToMap(formData);
        form.setAttribute("form", formDataMap);
        UpdateProfileContext userCtx = new UserUpdateProfileContext(context.getRealm(), context.getUser());
        form.setAttribute(UPDATE_PROFILE_CONTEXT_ATTR, userCtx);
        form.setAttribute("user", new ProfileBean(userCtx, formData));
        return form.createForm(VERIFY_PHONE_FORM_FTL);
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        if (UserUtils.isUserPhoneNumberVerified(context.getUser())) {
            context.success();
            return;
        }

        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        String existingPhone = context.getUser().getFirstAttribute(PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER);
        if (existingPhone != null) {
            formData.putSingle(PhoneConstants.FIELD_PHONE_NUMBER, existingPhone);
        }

        LoginFormsProvider form = context.form();
        context.challenge(createForm(context, form, formData));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        if (UserUtils.isUserPhoneNumberVerified(context.getUser())) {
            context.success();
            return;
        }

        KeycloakSession session = context.getSession();

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        MultivaluedMap<String, String> resFormData = new MultivaluedHashMap<>(formData);
        resFormData.remove(PhoneConstants.FIELD_VERIFICATION_CODE);

        LoginFormsProvider form = context.form();

        // Validate phone number
        PhoneNumber phoneNumber = new PhoneNumber(formData);
        if (!phoneNumber.isValid()) {
            form.addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.MISSING_PHONE_NUMBER));
            context.challenge(createForm(context, form, resFormData));
            return;
        }

        context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());

        // Check duplicate phone
        if (!ConfigUtils.isDuplicatePhoneAllowed(session)) {
            if (UserUtils.findUserByPhone(session, context.getRealm(), phoneNumber,
                    context.getUser().getId()).isPresent()) {
                form.addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.PHONE_EXISTS));
                context.challenge(createForm(context, form, resFormData));
                return;
            }
        }

        // Validate SMS verification code
        String verificationCode = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
        TokenCodeRepresentation tokenCode = ServiceUtils.getTokenCodeService(session)
                .currentProcess(phoneNumber, TokenCodeType.REGISTRATION);

        if (Validation.isBlank(verificationCode) || tokenCode == null
                || !tokenCode.getCode().equals(verificationCode)) {
            form.addError(new FormMessage(PhoneConstants.FIELD_VERIFICATION_CODE, PhoneConstants.SMS_CODE_MISMATCH));
            context.challenge(createForm(context, form, resFormData));
            return;
        }

        // Mark phone as verified
        ServiceUtils.getTokenCodeService(session)
                .tokenValidated(context.getUser(), phoneNumber, tokenCode.getId(), false);

        context.getEvent().success();
        context.success();
    }

    @Override
    public void close() {
    }
}
