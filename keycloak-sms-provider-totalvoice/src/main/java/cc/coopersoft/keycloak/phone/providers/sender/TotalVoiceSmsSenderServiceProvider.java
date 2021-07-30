package cc.coopersoft.keycloak.phone.providers.sender;

import br.com.totalvoice.TotalVoiceClient;
import br.com.totalvoice.api.Sms;
import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.FullSmsSenderAbstractService;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import org.json.JSONObject;
import org.keycloak.Config.Scope;

public class TotalVoiceSmsSenderServiceProvider implements MessageSenderService {

    private final Sms smsClient;

    TotalVoiceSmsSenderServiceProvider(Scope config, String realmDisplay) {
        TotalVoiceClient client = new TotalVoiceClient(config.get("authToken"));
        this.smsClient = new Sms(client);
    }

    @Override
    public MessageSendResult sendSmsMessage(TokenCodeType type, PhoneNumber phoneNumber, String message,
                                            int expires) throws MessageSendException {

        try {
            JSONObject response = smsClient.enviar(phoneNumber.getPhoneNumber(), message);

            if (!response.getBoolean("sucesso")) {
                throw new MessageSendException(response.getInt("status"),
                        String.valueOf(response.getInt("motivo")),
                        response.getString("mensagem"));
            }
            return new MessageSendResult(1).setResendExpires(120).setExpires(expires);
        } catch (Exception e) {
            return new MessageSendResult(-1).setError("500", e.getLocalizedMessage());
        }
    }

    @Override
    public void close() {
    }
}
