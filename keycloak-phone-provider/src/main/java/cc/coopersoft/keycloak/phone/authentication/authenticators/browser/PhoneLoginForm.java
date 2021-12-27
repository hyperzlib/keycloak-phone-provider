package cc.coopersoft.keycloak.phone.authentication.authenticators.browser;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.PhoneConstants;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import org.keycloak.authentication.AbstractFormAuthenticator;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.LoginBean;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

public class PhoneLoginForm extends AbstractFormAuthenticator implements Authenticator {

    protected static ServicesLogger log = ServicesLogger.LOGGER;

    public static final String PHONE_LOGIN_FORM_TPL = "login-with-phone.ftl";

    public static final String FIELD_LOGIN_TYPE = "loginType";

    public static final String USER_NOT_EXISTS = "userNotExists";

    private TokenCodeService getTokenCodeService(KeycloakSession session) {
        return session.getProvider(TokenCodeService.class);
    }

    protected Response challenge(AuthenticationFlowContext context, String error,
                                 MultivaluedMap<String, String> formData) {
        LoginFormsProvider form = context.form()
                .setExecution(context.getExecution().getId());
        if (error != null) form.setError(error);
        return makeForm(form);
    }

    protected Response makeForm(LoginFormsProvider form){
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        return makeForm(form, formData);
    }

    protected Response makeForm(LoginFormsProvider form, MultivaluedMap<String, String> formData){
        form.setAttribute("login", new LoginBean(formData));
        return form.createForm(PHONE_LOGIN_FORM_TPL);
    }

    public boolean enabledUser(AuthenticationFlowContext context, UserModel user,
                               MultivaluedMap<String, String> formData) {
        if (!user.isEnabled()) {
            context.getEvent().user(user);
            context.getEvent().error(Errors.USER_DISABLED);
            Response challengeResponse = challenge(context, Messages.ACCOUNT_DISABLED, formData);
            context.challenge(challengeResponse);
            return false;
        }
        return !isTemporarilyDisabledByBruteForce(context, user, formData);
    }

    protected boolean isTemporarilyDisabledByBruteForce(AuthenticationFlowContext context, UserModel user,
                                                        MultivaluedMap<String, String> formData) {
        if (context.getRealm().isBruteForceProtected()) {
            if (context.getProtector().isTemporarilyDisabled(context.getSession(), context.getRealm(), user)) {
                context.getEvent().user(user);
                context.getEvent().error(Errors.USER_TEMPORARILY_DISABLED);
                Response challengeResponse = challenge(context, Messages.INVALID_USER, formData);
                context.challenge(challengeResponse);
                return true;
            }
        }
        return false;
    }

    private boolean validateUser(AuthenticationFlowContext context, UserModel user,
                                 MultivaluedMap<String, String> inputData) {
        if (!enabledUser(context, user, inputData)) {
            return false;
        }
        String rememberMe = inputData.getFirst("rememberMe");
        boolean remember = rememberMe != null && rememberMe.equalsIgnoreCase("on");
        if (remember) {
            context.getAuthenticationSession().setAuthNote(Details.REMEMBER_ME, "true");
            context.getEvent().detail(Details.REMEMBER_ME, "true");
        } else {
            context.getAuthenticationSession().removeAuthNote(Details.REMEMBER_ME);
        }
        context.setUser(user);
        return true;
    }


    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        if (!formData.containsKey(FIELD_LOGIN_TYPE) || !formData.getFirst(FIELD_LOGIN_TYPE).equals("phone")){
            context.attempted();
            return;
        }

        if (formData.containsKey("cancel")) {
            context.cancelLogin();
            return;
        }

        KeycloakSession session = context.getSession();

        PhoneNumber phoneNumber = new PhoneNumber(formData);
        if (phoneNumber.isEmpty()) {
            context.challenge(challenge(context, PhoneConstants.MISSING_PHONE_NUMBER, formData));
            return;
        }
        UserModel user = UserUtils.findUserByPhone(session.users(), context.getRealm(), phoneNumber);
        if(user == null) { //用户不存在
            context.challenge(challenge(context, USER_NOT_EXISTS, formData));
            return;
        }
        String code = formData.getFirst(PhoneConstants.FIELD_VERIFICATION_CODE);
        if (Validation.isBlank(code)) {
            context.challenge(challenge(context, PhoneConstants.MISSING_VERIFY_CODE, formData));
            return;
        }
        TokenCodeService tokenCodeService = getTokenCodeService(session);
        if(!tokenCodeService.validateCode(user, phoneNumber, code, TokenCodeType.LOGIN)){ //验证码错误
            context.challenge(challenge(context, PhoneConstants.SMS_CODE_MISMATCH, formData));
            return;
        }

        //一切OK，返回最终值
        if(validateUser(context, user, formData)){
            context.success();
        }
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        context.challenge(makeForm(form));
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // never called
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // never called
    }

    @Override
    public void close() {

    }
}
