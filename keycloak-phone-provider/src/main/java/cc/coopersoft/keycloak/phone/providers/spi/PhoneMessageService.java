package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import org.keycloak.provider.Provider;


public interface PhoneMessageService extends Provider {
    MessageSendResult sendTokenCode(PhoneNumber phoneNumber, String sourceAddr, TokenCodeType type, String kind);
}
