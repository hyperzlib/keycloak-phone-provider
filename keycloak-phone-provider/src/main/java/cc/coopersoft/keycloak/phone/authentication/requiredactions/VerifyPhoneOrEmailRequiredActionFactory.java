package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import cc.coopersoft.keycloak.phone.authentication.requiredactions.util.EmailCooldownManager;
import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.policy.MaxAuthAgePasswordPolicyProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

@AutoService(RequiredActionFactory.class)
public class VerifyPhoneOrEmailRequiredActionFactory implements RequiredActionFactory {
    private Config.Scope config;

    @Override
    public String getDisplayText() {
        return "Verify Phone or Email";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return new VerifyPhoneOrEmailRequiredAction(config);
    }

    @Override
    public String getId() {
        return VerifyPhoneOrEmailRequiredAction.PROVIDER_ID;
    }

    @Override
    public void init(Config.Scope scope) {
        this.config = scope;
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {

        ProviderConfigProperty maxAge = new ProviderConfigProperty();
        maxAge.setName(Constants.MAX_AUTH_AGE_KEY);
        maxAge.setLabel("Maximum Age of Authentication");
        maxAge.setHelpText("Configures the duration in seconds this action can be used after the last authentication before the user is required to re-authenticate. " +
                "This parameter is used just in the context of AIA when the kc_action parameter is available in the request, which is for instance when user " +
                "himself updates his password in the account console.");
        maxAge.setType(ProviderConfigProperty.STRING_TYPE);
        maxAge.setDefaultValue(MaxAuthAgePasswordPolicyProviderFactory.DEFAULT_MAX_AUTH_AGE);

        return List.of(maxAge, EmailCooldownManager.createCooldownConfigProperty());
    }

    @Override
    public void close() {

    }
}
