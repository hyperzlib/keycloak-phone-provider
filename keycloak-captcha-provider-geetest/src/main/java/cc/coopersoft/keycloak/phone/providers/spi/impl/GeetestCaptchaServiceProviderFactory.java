package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.PhoneProviderCaptchaServiceProviderFactory;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneProviderCaptchaService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import com.google.auto.service.AutoService;

@AutoService(PhoneProviderCaptchaServiceProviderFactory.class)
public class GeetestCaptchaServiceProviderFactory implements PhoneProviderCaptchaServiceProviderFactory {
    private Config.Scope config;

    @Override
    public PhoneProviderCaptchaService create(KeycloakSession session) {
        GeetestCaptchaService geetestCaptchaService = new GeetestCaptchaService(session);
        geetestCaptchaService.setConfig(this.config);
        return geetestCaptchaService;
    }

    @Override
    public void init(Config.Scope config) {
        this.config = config;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "geetest-captcha";
    }
}
