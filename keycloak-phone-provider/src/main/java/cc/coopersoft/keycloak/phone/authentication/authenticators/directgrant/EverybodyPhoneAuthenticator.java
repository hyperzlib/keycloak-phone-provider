package cc.coopersoft.keycloak.phone.authentication.authenticators.directgrant;

import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.services.validation.Validation;


public class EverybodyPhoneAuthenticator extends AuthenticationCodeAuthenticator{

  private static final Logger logger = Logger.getLogger(EverybodyPhoneAuthenticator.class);

  public EverybodyPhoneAuthenticator(KeycloakSession session) {
    super(session);
  }

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public void authenticate(AuthenticationFlowContext context){
    PhoneNumber phoneNumber = getPhoneNumber(context);

    if (phoneNumber.isEmpty()){
      invalidCredentials(context);
      return;
    }

    String code = getAuthenticationCode(context);

    if (Validation.isBlank(code)){
      invalidCredentials(context);
      return;
    }

    TokenCodeService tokenCodeService = context.getSession().getProvider(TokenCodeService.class);
    TokenCodeRepresentation tokenCode = tokenCodeService.currentProcess(phoneNumber, TokenCodeType.OTP);

    if(tokenCode == null || !tokenCode.getCode().equals(code)){
      invalidCredentials(context);
      return;
    }

    UserModel user = UserUtils.findUserByPhone(context.getSession().users(),
            context.getRealm(), phoneNumber);
    if (user == null){
      if (context.getSession().users().getUserByUsername(context.getRealm(), phoneNumber.getPhoneNumber()) != null){
        invalidCredentials(context,AuthenticationFlowError.USER_CONFLICT);
        return;
      }
      user = context.getSession().users().addUser(context.getRealm(), phoneNumber.getPhoneNumber());
      user.setEnabled(true);
      context.getAuthenticationSession().setClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM, phoneNumber.getPhoneNumber());
    }
    context.setUser(user);

    tokenCodeService.tokenValidated(user,phoneNumber, tokenCode.getId());

    context.success();
  }
}
