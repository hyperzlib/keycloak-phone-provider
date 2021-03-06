package cc.coopersoft.keycloak.phone.providers.sender;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.spi.FullSmsSenderAbstractService;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public class TwilioSmsSenderServiceProvider implements MessageSenderService {

    private static final Logger logger = Logger.getLogger(TwilioSmsSenderServiceProvider.class);
    private final String twilioPhoneNumber;

    TwilioSmsSenderServiceProvider(Scope config, String realmDisplay) {
        Twilio.init(config.get("accountSid"), config.get("authToken"));
        this.twilioPhoneNumber = config.get("twilioPhoneNumber");

    }

    @Override
    public MessageSendResult sendSmsMessage(TokenCodeType type, String phoneNumber, String code, int expires) {

        Message msg = Message.creator(
                new PhoneNumber(phoneNumber),
                new PhoneNumber(twilioPhoneNumber),
                code).create();

        if (msg.getStatus() == Message.Status.FAILED) {
            return new MessageSendResult(-1).setError(String.valueOf(msg.getErrorCode()),
                    msg.getErrorMessage());
        } else {
            return new MessageSendResult(1).setResendExpires(120).setExpires(expires);
        }
    }

    @Override
    public void close() {
    }
}
