package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class VerifyPhoneOrEmailRequiredActionFactory implements RequiredActionFactory {
    public static String PROVIDER_ID = "VERIFY_PHONE_OR_EMAIL";
    private static final VerifyPhoneOrEmailRequiredAction instance = new VerifyPhoneOrEmailRequiredAction();

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
    public void init(Config.Scope scope) {

    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    @Override
    public void close() {

    }
}
