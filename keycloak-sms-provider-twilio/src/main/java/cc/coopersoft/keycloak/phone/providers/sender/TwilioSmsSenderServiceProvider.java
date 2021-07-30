package cc.coopersoft.keycloak.phone.providers.sender;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;

public class TwilioSmsSenderServiceProvider implements MessageSenderService {

    private static final Logger logger = Logger.getLogger(TwilioSmsSenderServiceProvider.class);
    private final String twilioPhoneNumber;

    TwilioSmsSenderServiceProvider(Scope config, String realmDisplay) {
        Twilio.init(config.get("accountSid"), config.get("authToken"));
        this.twilioPhoneNumber = config.get("twilioPhoneNumber");

    }

    @Override
    public MessageSendResult sendSmsMessage(TokenCodeType type, cc.coopersoft.keycloak.phone.utils.PhoneNumber phoneNumber,
                                            String code, int expires) {

        Message msg = Message.creator(
                new PhoneNumber(phoneNumber.toString()),
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
