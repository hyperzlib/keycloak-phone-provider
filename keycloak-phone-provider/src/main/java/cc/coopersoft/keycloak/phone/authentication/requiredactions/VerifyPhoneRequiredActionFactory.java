package cc.coopersoft.keycloak.phone.authentication.requiredactions;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

@AutoService(RequiredActionFactory.class)
public class VerifyPhoneRequiredActionFactory implements RequiredActionFactory {

    private Config.Scope config;

    @Override
    public String getDisplayText() {
        return "Verify Phone Number";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return new VerifyPhoneRequiredAction(config);
    }

    @Override
    public String getId() {
        return VerifyPhoneRequiredAction.PROVIDER_ID;
    }

    @Override
    public void init(Config.Scope scope) {
        this.config = scope;
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }
}
