package cc.coopersoft.keycloak.phone.authentication.authenticators.browser;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.browser.WebAuthnConditionalUIAuthenticator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.credential.WebAuthnCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.services.ServicesLogger;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@AutoService(AuthenticatorFactory.class)
public class PhoneOrPasswordLoginFormFactory implements AuthenticatorFactory {
    protected static ServicesLogger log = ServicesLogger.LOGGER;

    public static final String PROVIDER_ID = "auth-phone-password-login-form";
    public static final String CONF_DEFAULT_LOGIN_METHOD = "defaultLoginMethod";

    @Override
    public Authenticator create(KeycloakSession session) {
        return new PhoneOrPasswordLoginForm(session);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getReferenceCategory() {
        return "phone";
    }

    @Override
    public Set<String> getOptionalReferenceCategories(KeycloakSession session) {
        return WebAuthnConditionalUIAuthenticator.isPasskeysEnabled(session)
                ? Collections.singleton(WebAuthnCredentialModel.TYPE_PASSWORDLESS)
                : AuthenticatorFactory.super.getOptionalReferenceCategories(session);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // 允许选择默认登录方式是手机号还是用户名密码
        return ProviderConfigurationBuilder.create()
                .property()
                .name(PhoneOrPasswordLoginFormFactory.CONF_DEFAULT_LOGIN_METHOD)
                .type(ProviderConfigProperty.LIST_TYPE)
                .label("Default Login Method")
                .helpText("Select the default login method for users.")
                .options("phone", "username")
                .defaultValue("username")
                .add()
                .build();
    }

    public static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED
    };

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public String getDisplayType() {
        return "Phone or Username Password Login Form";
    }

    @Override
    public String getHelpText() {
        return "Validates phone sms-code or username password from login form.";
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

}
