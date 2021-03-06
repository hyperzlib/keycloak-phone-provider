package cc.coopersoft.keycloak.phone.providers.spi;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;

public abstract class FullSmsSenderAbstractService implements MessageSenderService{

    private final String realmDisplay;

    public FullSmsSenderAbstractService(String realmDisplay) {
        this.realmDisplay = realmDisplay;
    }

    public abstract MessageSendResult sendMessage(String phoneNumber, String message) throws MessageSendException;


    public MessageSendResult sendSmsMessage(TokenCodeType type, String phoneNumber, String code , int expires) throws MessageSendException{
        final String MESSAGE = String.format("[%s] - " + type.getLabel() + " code: %s, expires: %s minute ",realmDisplay , code, expires / 60);
        return sendMessage(phoneNumber,MESSAGE);
    }
}
