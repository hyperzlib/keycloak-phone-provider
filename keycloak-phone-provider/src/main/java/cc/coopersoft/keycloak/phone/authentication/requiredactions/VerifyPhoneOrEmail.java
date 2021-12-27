package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import org.jboss.logging.Logger;
import org.keycloak.authentication.DisplayTypeRequiredActionFactory;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.requiredactions.VerifyEmail;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

public class VerifyPhoneOrEmail extends VerifyEmail implements RequiredActionProvider,
        RequiredActionFactory, DisplayTypeRequiredActionFactory {
    public static String PROVIDER_ID = "VERIFY_PHONE_OR_EMAIL";
    private static final Logger logger = Logger.getLogger(VerifyPhoneOrEmail.class);
    private static final VerifyPhoneOrEmail instance = new VerifyPhoneOrEmail();

    @Override
    public String getDisplayText() {
        return "Verify Phone or Email";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return instance;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        if (!context.getUser().isEmailVerified() && isUserPhoneNumberVerified(context.getUser())) {
            context.getUser().addRequiredAction(PROVIDER_ID);
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
        return phoneNumber != null && !phoneNumber.trim().equals("");
    }

    @Override
    public void close() {

    }
}
