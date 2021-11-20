package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

public class UpdatePhoneNumberRequiredAction implements RequiredActionProvider {

    public static final String PROVIDER_ID = "UPDATE_PHONE_NUMBER";

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Response challenge = context.form()
                .createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent();
        event.event(EventType.UPDATE_PROFILE);
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        UserModel user = context.getUser();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        String newPhoneNumber = formData.getFirst(PhoneConstants.FIELD_PHONE_NUMBER);
        String oldPhoneNumber = user.getFirstAttribute(PhoneConstants.FIELD_PHONE_NUMBER);

        if (!newPhoneNumber.equals(oldPhoneNumber)) {
            Response challenge = context.form()
                    .setError("User cannot change phone number via rest api")
                    .setFormData(formData)
                    .createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
            context.challenge(challenge);
            return;
        }
        context.success();
    }

    @Override
    public void close() {
    }
}
