package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.CaptchaServiceProviderFactory;
import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class GeetestCaptchaServiceProviderFactory implements CaptchaServiceProviderFactory {
    private Config.Scope config;

    @Override
    public CaptchaService create(KeycloakSession session) {
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
        return "geetest";
    }
}
