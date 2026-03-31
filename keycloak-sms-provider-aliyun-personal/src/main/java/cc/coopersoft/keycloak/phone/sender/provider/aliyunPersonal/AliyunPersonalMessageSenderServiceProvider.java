package cc.coopersoft.keycloak.phone.sender.provider.aliyunPersonal;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.RealmModel;

public class AliyunPersonalMessageSenderServiceProvider implements MessageSenderService {
    private static final Logger logger = Logger.getLogger(AliyunPersonalMessageSenderServiceProvider.class);
    private final Config.Scope config;
    private final RealmModel realm;

    public AliyunPersonalMessageSenderServiceProvider(Config.Scope config, RealmModel realm) {
        this.config = config;
        this.realm = realm;
    }

    private String getConfig(String realm, String type, String key){
        realm = realm.toLowerCase();
        type = type.toLowerCase();
        key = key.toLowerCase();
        String value = config.get(key + "-" + realm + "-" + type);
        if(value == null) {
            value = config.get(key + "-" + type);
            if(value == null) {
                value = config.get(key);
            }
        }
        return value;
    }

    public com.aliyun.dypnsapi20170525.Client createClient() throws Exception {
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        com.aliyun.teaopenapi.models.Config aiyunConfig = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(config.get("accessKeyId"))
                .setAccessKeySecret(config.get("accessKeySecret"));
        aiyunConfig.endpoint = "dypnsapi.aliyuncs.com";
        return new Client(aiyunConfig);
    }

    @Override
    public MessageSendResult sendSmsMessage(TokenCodeType type, PhoneNumber phoneNumber, String code, int expires) {
        String templateId = this.getConfig(realm.getName(), type.name(), "template");
        String signName = this.getConfig(realm.getName(), type.name(), "signName");
        int expiresMin = (int)Math.floor(expires / 60f);
        int resendExpires = config.getInt("resendExpires", 120);

        com.aliyun.dypnsapi20170525.Client client;
        try {
            client = createClient();
        } catch (Exception e) {
            logger.error("Cannot create Aliyun Personal client", e);
            return new MessageSendResult(-1)
                    .setError("CANNOT_CREATE_CLIENT", e.getMessage());
        }

        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setCodeLength(6L)
                .setCountryCode(phoneNumber.getAreaCode())
                .setPhoneNumber(phoneNumber.getPhoneNumber())
                .setSignName(signName)
                .setTemplateCode(templateId)
                .setValidTime((long)expires)
                .setInterval((long)resendExpires)
                .setTemplateParam(String.format("{\"code\":\"##code##\",\"min\":%d}", expiresMin))
                .setReturnVerifyCode(true);


        try {
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            if (!com.aliyun.teautil.Common.equalString(response.body.code, "OK")) {
                return new MessageSendResult(-1)
                        .setError(response.body.code, response.body.message);
            }

            return new MessageSendResult(1)
                    .setSmsCode(response.body.model.verifyCode)
                    .setResendExpires(resendExpires)
                    .setExpires(expires);
        } catch (Exception e) {
            logger.error("Send SMS message error", e);
            return new MessageSendResult(-1)
                    .setError("SEND_SMS_ERROR", e.getMessage());
        }
    }

    @Override
    public void close() {

    }
}
