package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

import java.util.Date;

public interface TokenCodeService extends Provider {

    TokenCodeRepresentation currentProcess(String phoneNumber, TokenCodeType tokenCodeType);

    void removeCode(String phoneNumber, TokenCodeType tokenCodeType);

    boolean canResend(String phoneNumber, TokenCodeType tokenCodeType);

    boolean isAbusing(String phoneNumber, TokenCodeType tokenCodeType);

    void persistCode(TokenCodeRepresentation tokenCode, TokenCodeType tokenCodeType, MessageSendResult sendResult);

    boolean validateCode(String phoneNumber, String code);

    boolean validateCode(String phoneNumber, String code, TokenCodeType tokenCodeType);

    boolean validateCode(UserModel user, String phoneNumber, String code);

    boolean validateCode(UserModel user, String phoneNumber, String code, TokenCodeType tokenCodeType);

    void setUserPhoneNumberByCode(UserModel user, String phoneNumber, String code);

    void cleanUpAction(UserModel user);

    void tokenValidated(UserModel user, String phoneNumber, String tokenCodeId);

    Date getResendExpires(String phoneNumber, TokenCodeType tokenCodeType);
}
