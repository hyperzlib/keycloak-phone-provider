package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import org.keycloak.provider.Provider;


public interface PhoneMessageService extends Provider {

    //TODO on key longin support
    //boolean Verification(String phoneNumber, String token);

    MessageSendResult sendTokenCode(String phoneNumber, TokenCodeType type);
}
