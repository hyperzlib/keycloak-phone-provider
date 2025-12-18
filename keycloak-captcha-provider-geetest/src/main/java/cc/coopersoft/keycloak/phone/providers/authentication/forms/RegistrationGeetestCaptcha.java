package cc.coopersoft.keycloak.phone.providers.authentication.forms;

import cc.coopersoft.keycloak.phone.providers.spi.PhoneProviderCaptchaService;
import cc.coopersoft.keycloak.phone.providers.spi.impl.GeetestCaptchaService;
import com.google.auto.service.AutoService;
import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;

import java.util.ArrayList;
import java.util.List;

@AutoService(FormActionFactory.class)
public class RegistrationGeetestCaptcha implements FormAction, FormActionFactory {
    public static final String PROVIDER_ID = "registration-geetest-captcha";

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        form.setAttribute("geetestCaptchaRequired", true);
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        PhoneProviderCaptchaService geetestCaptchaService = context.getSession()
                .getProvider(PhoneProviderCaptchaService.class, GeetestCaptchaService.ID);

        String username = formData.getFirst("username");

        if (!geetestCaptchaService.verify(formData, username)) {
            List<FormMessage> errors = new ArrayList<>();
            context.error(Errors.INVALID_REGISTRATION);
            errors.add(new FormMessage("geetest", Messages.RECAPTCHA_FAILED));
            context.validationError(formData, errors);
            return;
        }

        context.success();
    }

    @Override
    public void success(FormContext context) {
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public String getDisplayType() {
        return "Geetest Captcha for Registration";
    }

    @Override
    public String getReferenceCategory() {
        return "captcha";
    }

    @Override
    public String getHelpText() {
        return "Adds Geetest Captcha to the form.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
