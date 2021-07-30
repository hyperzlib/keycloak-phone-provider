/**
 * add by zhangzhl
 * 2020-07-27
 * 注册页手机号码必填验证
 *
 */
package cc.coopersoft.keycloak.phone.authentication.forms;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationPage;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

public class RegistrationPhoneNumberOrEmail implements FormAction, FormActionFactory {

	private static final Logger logger = Logger.getLogger(RegistrationPhoneNumberOrEmail.class);

	public static final String PROVIDER_ID = "registration-phone-or-email";

	public static final String MISSING_PHONE_NUMBER_OR_EMAIL = "requiredPhoneNumberOrEmail";

	public static final String PHONE_IN_USE = "phone-in-use";

	@Override
	public String getHelpText() {
		return "valid phone number and verification code or using email";
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
		return "Phone Or Email Validation";
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
			AuthenticationExecutionModel.Requirement.REQUIRED,
			AuthenticationExecutionModel.Requirement.DISABLED
	};

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
		String eventError = Errors.INVALID_REGISTRATION;
		KeycloakSession session = context.getSession();
		PhoneNumber phoneNumber = new PhoneNumber(formData);

		if(!phoneNumber.isEmpty()){
			//使用手机号注册
			formData.remove(PhoneConstants.FIELD_EMAIL);
			context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());

			if (!UserUtils.isDuplicatePhoneAllowed() &&
					UserUtils.findUserByPhone(session.users(), context.getRealm(), phoneNumber) != null) {
				formData.remove(PhoneConstants.FIELD_PHONE_NUMBER);
				eventError = PHONE_IN_USE;
				context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
				errors.add(new FormMessage(PhoneConstants.FIELD_PHONE_NUMBER, PhoneConstants.PHONE_EXISTS));
			} else {
				//检查短信验证码
				String verificationCode = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
				TokenCodeRepresentation tokenCode =  getTokenCodeService(session)
						.currentProcess(phoneNumber, TokenCodeType.REGISTRATION);
				if (Validation.isBlank(verificationCode) || tokenCode == null ||
						!tokenCode.getCode().equals(verificationCode)){
					context.error(Errors.INVALID_REGISTRATION);
					context.getEvent().detail(PhoneConstants.FIELD_PHONE_NUMBER, phoneNumber.getFullPhoneNumber());
					errors.add(new FormMessage(PhoneConstants.FIELD_VERIFICATION_CODE,
							PhoneConstants.SMS_CODE_MISMATCH));
				}
				context.getSession().setAttribute(PhoneConstants.FIELD_TOKEN_ID, tokenCode.getId());
			}
		} else if(formData.containsKey(PhoneConstants.FIELD_EMAIL) &&
				!Validation.isBlank(formData.getFirst(PhoneConstants.FIELD_EMAIL))) {
			//使用邮箱注册，验证电子邮箱
			formData.remove(PhoneConstants.FIELD_AREA_CODE);
			formData.remove(PhoneConstants.FIELD_PHONE_NUMBER);
			String email = formData.getFirst(Validation.FIELD_EMAIL);
			boolean emailValid = true;
			if (Validation.isBlank(email)) {
				errors.add(new FormMessage(RegistrationPage.FIELD_EMAIL, Messages.MISSING_EMAIL));
				emailValid = false;
			} else if (!Validation.isEmailValid(email)) {
				context.getEvent().detail(Details.EMAIL, email);
				errors.add(new FormMessage(RegistrationPage.FIELD_EMAIL, Messages.INVALID_EMAIL));
				emailValid = false;
			}

			if (emailValid && !context.getRealm().isDuplicateEmailsAllowed()) {
				boolean duplicateEmail = false;
				try {
					if(session.users().getUserByEmail(context.getRealm(), email) != null) {
						duplicateEmail = true;
					}
				} catch (ModelDuplicateException e) {
					duplicateEmail = true;
				}
				if (duplicateEmail) {
					eventError = Errors.EMAIL_IN_USE;
					formData.remove(Validation.FIELD_EMAIL);
					context.getEvent().detail(Details.EMAIL, email);
					errors.add(new FormMessage(RegistrationPage.FIELD_EMAIL, Messages.EMAIL_EXISTS));
				}
			}
		} else {
			//缺少参数
			eventError = Errors.INVALID_INPUT;
			errors.add(new FormMessage(null, MISSING_PHONE_NUMBER_OR_EMAIL));
		}
		if (errors.size() > 0) {
			context.error(eventError);
			context.validationError(formData, errors);
		} else {
			context.success();
		}
	}

	@Override
	public void success(FormContext context) {
		UserModel user = context.getUser();
		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		user.setFirstName(formData.getFirst(RegistrationPage.FIELD_FIRST_NAME));
		user.setLastName(formData.getFirst(RegistrationPage.FIELD_LAST_NAME));
		if(formData.containsKey(PhoneConstants.FIELD_PHONE_NUMBER)) {
			PhoneNumber phoneNumber = new PhoneNumber(formData);
			String tokenId = context.getSession().getAttribute(PhoneConstants.FIELD_TOKEN_ID, String.class);

			logger.info(String.format("registration user %s phone success, tokenId is: %s", user.getId(), tokenId));
			getTokenCodeService(context.getSession()).tokenValidated(user, phoneNumber, tokenId);
		} else {
			logger.info(String.format("registration user %s by email success.", user.getId()));
			user.setEmail(formData.getFirst(RegistrationPage.FIELD_EMAIL));
		}
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
