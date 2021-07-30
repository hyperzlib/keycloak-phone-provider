package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

import java.util.Date;

public interface TokenCodeService extends Provider {

    TokenCodeRepresentation currentProcess(PhoneNumber phoneNumber, TokenCodeType tokenCodeType);

    void removeCode(PhoneNumber phoneNumber, TokenCodeType tokenCodeType);

    boolean canResend(PhoneNumber phoneNumber, TokenCodeType tokenCodeType);

    boolean isAbusing(PhoneNumber phoneNumber, TokenCodeType tokenCodeType);

    void persistCode(TokenCodeRepresentation tokenCode, TokenCodeType tokenCodeType, MessageSendResult sendResult);

    boolean validateCode(PhoneNumber phoneNumber, String code);

    boolean validateCode(PhoneNumber phoneNumber, String code, TokenCodeType tokenCodeType);

    boolean validateCode(UserModel user, PhoneNumber phoneNumber, String code);

    boolean validateCode(UserModel user, PhoneNumber phoneNumber, String code, TokenCodeType tokenCodeType);

    void setUserPhoneNumberByCode(UserModel user, PhoneNumber phoneNumber, String code);

    void cleanUpAction(UserModel user);

    void tokenValidated(UserModel user, PhoneNumber phoneNumber, String tokenCodeId);

    Date getResendExpires(PhoneNumber phoneNumber, TokenCodeType tokenCodeType);
}
