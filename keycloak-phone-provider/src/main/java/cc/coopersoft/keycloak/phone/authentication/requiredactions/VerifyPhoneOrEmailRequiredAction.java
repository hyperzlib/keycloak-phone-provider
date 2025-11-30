package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import org.jboss.logging.Logger;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.requiredactions.VerifyEmail;
import org.keycloak.models.Constants;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

public class VerifyPhoneOrEmailRequiredAction extends VerifyEmail implements RequiredActionProvider {
    private static final Logger logger = Logger.getLogger(VerifyPhoneOrEmailRequiredAction.class);

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        if (!context.getUser().isEmailVerified() && isUserPhoneNumberVerified(context.getUser())) {
            context.getUser().addRequiredAction(VerifyPhoneOrEmailRequiredActionFactory.PROVIDER_ID);
            logger.debug("User is required to verify phone or email");
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        if (isUserPhoneNumberVerified(context.getUser())) {
            context.success();
            authSession.removeAuthNote(Constants.VERIFY_EMAIL_KEY);
            return;
        }

        super.requiredActionChallenge(context);
    }

    private boolean isUserPhoneNumberVerified(UserModel user) {
        String phoneNumber = user.getFirstAttribute(PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER);
        return phoneNumber != null && !phoneNumber.trim().isEmpty();
    }
}
