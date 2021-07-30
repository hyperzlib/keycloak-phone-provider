/*
  add by zhangzhl
  2020-07-27
  注册页手机号码必填验证
 */
package cc.coopersoft.keycloak.phone.authentication.forms;

import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.validation.Validation;

import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

public class RegistrationPhoneNumber implements FormAction, FormActionFactory {

	private static final Logger logger = Logger.getLogger(RegistrationPhoneNumber.class);

	public static final String PROVIDER_ID = "registration-phone";

	@Override
	public String getHelpText() {
		return "valid phone number and verification code";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return null;
	}

	@Override
	public boolean isUserSetupAllowed() {
		return false;
	}

	@Override
	public void close() {

	}
	@Override
	public String getDisplayType() {
		return "Phone Validation";
	}

	@Override
	public String getReferenceCategory() {
		return null;
	}

	@Override
	public boolean isConfigurable() {
		return false;
	}

	private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
			AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED };

	@Override
	public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
		return REQUIREMENT_CHOICES;
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
	public String getId() {
		return PROVIDER_ID;
	}

	// FormAction
	private TokenCodeService getTokenCodeService(KeycloakSession session){
		return session.getProvider(TokenCodeService.class);
	}

	@Override
	public void validate(ValidationContext context) {
		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		List<FormMessage> errors = new ArrayList<>();
		context.getEvent().detail(Details.REGISTER_METHOD, "form");

		KeycloakSession session = context.getSession();

		PhoneNumber phoneNumber = new PhoneNumber(formData);
		context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());

		if (phoneNumber.isEmpty()) {
			context.error(Errors.INVALID_REGISTRATION);
			errors.add(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.MISSING_PHONE_NUMBER));
			context.validationError(formData, errors);
			return;
		}

		if (!UserUtils.isDuplicatePhoneAllowed() && UserUtils.findUserByPhone(session.users(),context.getRealm(), phoneNumber) != null) {
			formData.remove(PhoneConstants.FIELD_PHONE_NUMBER);
			context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
			errors.add(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.PHONE_EXISTS));
			context.error(Errors.INVALID_REGISTRATION);
			context.validationError(formData, errors);
			return;
		}

		String verificationCode = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
		TokenCodeRepresentation tokenCode =  getTokenCodeService(session).currentProcess(phoneNumber,
				TokenCodeType.REGISTRATION);
		if (Validation.isBlank(verificationCode) || tokenCode == null || !tokenCode.getCode().equals(verificationCode)){
			context.error(Errors.INVALID_REGISTRATION);
			formData.remove(PhoneConstants.FIELD_VERIFICATION_CODE);
			errors.add(new FormMessage(PhoneConstants.FIELD_VERIFICATION_CODE, PhoneConstants.SMS_CODE_MISMATCH));
			context.validationError(formData, errors);
			return;
		}

		context.getSession().setAttribute(PhoneConstants.FIELD_TOKEN_ID, tokenCode.getId());
		context.success();
	}

	@Override
	public void success(FormContext context) {
		UserModel user = context.getUser();

		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		PhoneNumber phoneNumber = new PhoneNumber(formData);
		String tokenId = context.getSession().getAttribute(PhoneConstants.FIELD_TOKEN_ID, String.class);

		logger.info(String.format("registration user %s phone success, tokenId is: %s", user.getId(), tokenId));
		getTokenCodeService(context.getSession()).tokenValidated(user, phoneNumber, tokenId);
	}

	@Override
	public void buildPage(FormContext context, LoginFormsProvider form) {
		form.setAttribute("phoneNumberRequired", true);
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
}
