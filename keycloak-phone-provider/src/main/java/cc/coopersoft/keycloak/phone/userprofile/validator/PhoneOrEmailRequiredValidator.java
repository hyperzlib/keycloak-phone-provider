package cc.coopersoft.keycloak.phone.userprofile.validator;

import cc.coopersoft.keycloak.phone.providers.constants.PhoneProviderMessages;
import com.google.auto.service.AutoService;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.validate.*;

import cc.coopersoft.keycloak.phone.utils.PhoneConstants;

import java.util.ArrayList;
import java.util.List;

@AutoService(ValidatorFactory.class)
public class PhoneOrEmailRequiredValidator implements SimpleValidator, ConfiguredProvider {
    public static final String ID = "up-phone-or-email-required";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();


    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ValidationContext validate(Object input, String inputHint, ValidationContext context, ValidatorConfig config) {
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) input;

        String value = null;

        if (values != null && !values.isEmpty()) {
            value = values.get(0);
        }

        KeycloakSession session = context.getSession();

        String email = null;
        String phoneNumber = null;

        if (Validation.isBlank(value)) {
            UserModel user = UserProfileAttributeValidationContext.from(context).getAttributeContext().getUser();
            if (user == null) {
                // Skip validation if user is not available (e.g., during registration)
                // Registration flow will validate required attributes separately
                return context;
            }

            switch (inputHint) {
                case PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER -> {
                    email = user.getEmail();
                    phoneNumber = value;
                }
                case Validation.FIELD_EMAIL -> {
                    email = value;
                    phoneNumber = user.getFirstAttribute(PhoneConstants.USER_ATTRIBUTE_FIELD_PHONE_NUMBER);
                }
            }

            if (Validation.isBlank(phoneNumber) && Validation.isBlank(email)) {
                context.addError(new ValidationError(ID, inputHint, PhoneProviderMessages.REQUIRE_PHONE_NUMBER_OR_EMAIL));
            }
        }

        return context;
    }

    @Override
    public String getHelpText() {
        return "Validates that user has at least a phone number or an email address.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }
}
