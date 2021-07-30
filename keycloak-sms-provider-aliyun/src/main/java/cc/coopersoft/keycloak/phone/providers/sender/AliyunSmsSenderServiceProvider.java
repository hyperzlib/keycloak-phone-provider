package cc.coopersoft.keycloak.phone.providers.sender;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.keycloak.Config;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.RealmModel;

public class AliyunSmsSenderServiceProvider implements MessageSenderService {

    private final Config.Scope config;
    private final RealmModel realm;
    private final IAcsClient client;

    public AliyunSmsSenderServiceProvider(Config.Scope config, RealmModel realm) {
        this.config = config;
        this.realm = realm;
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", config.get("accessKeyId"), config.get("accessSecret"));
        client = new DefaultAcsClient(profile);
    }

    private String getConfig(String realm, String type, String key){
        realm = realm.toUpperCase();
        type = type.toUpperCase();
        key = key.toUpperCase();
        String value = config.get(realm + "_" + type + "_" + key);
        if(value == null){
            value = config.get(type + "_" + key);
            if(value == null){
                value = config.get("DEFAULT_" + key);
            }
        }
        return value;
    }

    @Override
    public MessageSendResult sendSmsMessage(TokenCodeType type, PhoneNumber phoneNumber, String code, int expires) {
        String templateId = this.getConfig(realm.getName(), type.name(), "template");
        String signName = this.getConfig(realm.getName(), type.name(), "signName");
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("dysmsapi.aliyuncs.com");
        request.setSysVersion("2017-05-25");
        request.setSysAction("SendSms");
        request.putQueryParameter("RegionId", "cn-hangzhou");
        request.putQueryParameter("PhoneNumbers", phoneNumber.getPhoneNumber());
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", templateId);

        request.putQueryParameter("TemplateParam", String.format("{\"code\":\"%s\"}", code));
        try {
            CommonResponse response = client.getCommonResponse(request);
            System.out.println(response.getData());
            return new MessageSendResult(1).setResendExpires(120).setExpires(expires);
        } catch (ServerException e) {
            e.printStackTrace();
            return new MessageSendResult(-1).setError(e.getErrCode(), e.getErrMsg());
        } catch (ClientException e) {
            return new MessageSendResult(-1).setError(e.getErrCode(), e.getErrMsg());
        }
    }

    @Override
    public void close() {

    }
}
