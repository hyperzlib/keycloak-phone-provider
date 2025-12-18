package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.utils.*;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.requiredactions.util.UpdateProfileContext;
import org.keycloak.authentication.requiredactions.util.UserUpdateProfileContext;
import org.keycloak.events.Details;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.ProfileBean;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;

import java.util.Map;

import static org.keycloak.forms.login.LoginFormsProvider.UPDATE_PROFILE_CONTEXT_ATTR;

public class UpdatePhoneNumberRequiredAction implements RequiredActionProvider {
    public static final String PROVIDER_ID = "UPDATE_PHONE_NUMBER";
    public static final String UPDATE_PHONE_NUMBER_FORM_FTL = "update-phone-number.ftl";

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
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
        return form.createForm(UPDATE_PHONE_NUMBER_FORM_FTL);
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        LoginFormsProvider form = context.form();
        Response challenge = createForm(context, form, null);
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent();
        UserModel user = context.getUser();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        MultivaluedMap<String, String> resFormData = new MultivaluedHashMap<>(formData);
        resFormData.remove(PhoneConstants.FIELD_VERIFICATION_CODE);

        PhoneNumber phoneNumber = new PhoneNumber(formData);
        String oldPhoneNumberStr = user.getFirstAttribute(PhoneConstants.FIELD_PHONE_NUMBER);

        if (phoneNumber.isEmpty()) {
            // Remove phone number
            user.removeAttribute(PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER);
            user.removeAttribute(PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER_VERIFIED);
            context.success();
            return;
        }

        if (phoneNumber.toString().equals(oldPhoneNumberStr)) {
            // Phone number not changed
            context.success();
            return;
        }


        // Validate new phone number
        context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());

        LoginFormsProvider form = context.form();
        if (!ConfigUtils.isDuplicatePhoneAllowed(session) &&
                UserUtils.findUserByPhone(session, realm, phoneNumber).isPresent()) {
            formData.remove(PhoneConstants.FIELD_PHONE_NUMBER);
            event.detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
            form.addError(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.PHONE_EXISTS));

            context.challenge(createForm(context, form, resFormData));
            return;
        }

        // Check verification code
        String verificationCode = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
        TokenCodeRepresentation tokenCode = ServiceUtils.getTokenCodeService(session)
                .currentProcess(phoneNumber, TokenCodeType.REGISTRATION);
        if (Validation.isBlank(verificationCode) || tokenCode == null ||
                !tokenCode.getCode().equals(verificationCode)) {
            event.detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
            form.addError(new FormMessage(PhoneConstants.FIELD_VERIFICATION_CODE, PhoneConstants.SMS_CODE_MISMATCH));
            context.challenge(createForm(context, form, resFormData));
            return;
        }

        ServiceUtils.getTokenCodeService(session).tokenValidated(user, phoneNumber, tokenCode.getId(), false);

        event.success();
        context.success();
    }

    @Override
    public void close() {
    }
}
