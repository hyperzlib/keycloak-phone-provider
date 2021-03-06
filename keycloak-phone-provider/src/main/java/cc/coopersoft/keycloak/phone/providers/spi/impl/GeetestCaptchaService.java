package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;
import com.sdk.GeetestLib;
import com.sdk.GeetestLibResult;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.managers.AuthenticationManager;

import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;

public class GeetestCaptchaService implements CaptchaService {
    private static final Logger log = Logger.getLogger(GeetestCaptchaService.class);
    private static int serverStatus = 1;

    private final KeycloakSession session;
    private Config.Scope config;

    public GeetestCaptchaService(KeycloakSession session) {
        this.session = session;
    }

    private String getUserIdByAuthResult(AuthenticationManager.AuthResult user){
        String uid = "guest";
        if(user != null){
            uid = user.getUser().getId();
        }
        return uid;
    }

    @Override
    public boolean verify(final MultivaluedMap<String, String> formParams, AuthenticationManager.AuthResult user) {
        return this.verify(formParams, this.getUserIdByAuthResult(user));
    }

    @Override
    public boolean verify(final MultivaluedMap<String, String> formParams, String user) {
        if(user == null) user = "unknow";

        String geetestId = this.config.get("id");
        String geetestKey = this.config.get("key");
        if(geetestId == null || geetestKey == null){
            //如果没有设置key就直接通过
            //出事了别怪我
            return true;
        }

        KeycloakContext context = session.getContext();

        String challenge = formParams.getFirst(GeetestLib.GEETEST_CHALLENGE);
        String validate = formParams.getFirst(GeetestLib.GEETEST_VALIDATE);
        String seccode = formParams.getFirst(GeetestLib.GEETEST_SECCODE);

        if(challenge == null || validate == null || seccode == null){
            return false;
        }

        GeetestLib gtLib = new GeetestLib(geetestId, geetestKey);

        GeetestLibResult result = null;
        if (serverStatus == 1) {
            Map<String, String> paramMap = new HashMap<String, String>();
            paramMap.put("user_id", user);
            paramMap.put("client_type", "web");
            paramMap.put("ip_address", context.getConnection().getRemoteAddr());
            result = gtLib.successValidate(challenge, validate, seccode, paramMap);
        } else {
            result = gtLib.failValidate(challenge, validate, seccode);
        }

        return result.getStatus() == 1;
    }

    @Override
    public String getFrontendKey(AuthenticationManager.AuthResult user){
        return this.getFrontendKey(this.getUserIdByAuthResult(user));
    }

    @Override
    public String getFrontendKey(String user){
        if(user == null) user = "unknow";

        String geetestId = this.config.get("id");
        String geetestKey = this.config.get("key");
        if(geetestId == null || geetestKey == null){
            return "{\"success\":0,\"message\": \"unset geetest id or key in your config.\"}";
        }

        KeycloakContext context = session.getContext();
        //生成极验的code
        GeetestLib gtLib = new GeetestLib(geetestId, geetestKey);
        String digestmod = "md5";

        Map<String,String> paramMap = new HashMap<String, String>();
        paramMap.put("digestmod", digestmod);
        paramMap.put("user_id", user);
        paramMap.put("client_type", "web");
        paramMap.put("ip_address", context.getConnection().getRemoteAddr());
        GeetestLibResult result = gtLib.register(digestmod, paramMap);
        serverStatus = result.getStatus();
        return result.getData();
    }

    public void setConfig(Config.Scope config){
        this.config = config;
    }

    @Override
    public void close() {
    }
}
