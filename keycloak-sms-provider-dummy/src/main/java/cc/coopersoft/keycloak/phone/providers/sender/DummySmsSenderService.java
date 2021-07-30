package cc.coopersoft.keycloak.phone.providers.sender;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.spi.FullSmsSenderAbstractService;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import org.jboss.logging.Logger;

import java.util.Random;

public class DummySmsSenderService extends FullSmsSenderAbstractService {

    private static final Logger logger = Logger.getLogger(DummySmsSenderService.class);

    public DummySmsSenderService(String realmDisplay) {
        super(realmDisplay);
    }

    @Override
    public MessageSendResult sendMessage(String phoneNumber, String message) throws MessageSendException {

        // here you call the method for sending messages
        logger.info(String.format("To: %s >>> %s", phoneNumber, message));

        // simulate a failure
        if (new Random().nextInt(10) % 5 == 0) {
            throw new MessageSendException(500, "MSG0042", "Insufficient credits to send message");
        }
        return new MessageSendResult(1).setExpires(0).setResendExpires(120);
    }

    @Override
    public MessageSendResult sendSmsMessage(TokenCodeType type, PhoneNumber phoneNumber, String message,
                                            int expires) throws MessageSendException {

        // here you call the method for sending messages
        logger.info(String.format("To: %s >>> %s", phoneNumber, message));

        // simulate a failure
        if (new Random().nextInt(10) % 5 == 0) {
            throw new MessageSendException(500, "MSG0042", "Insufficient credits to send message");
        }
        return new MessageSendResult(1).setExpires(0).setResendExpires(120);
    }

    @Override
    public void close() {
    }
}
