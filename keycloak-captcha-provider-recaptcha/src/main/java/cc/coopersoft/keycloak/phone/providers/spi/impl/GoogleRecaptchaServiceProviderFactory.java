package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.CaptchaService;
import cc.coopersoft.keycloak.phone.providers.spi.CaptchaServiceProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class GoogleRecaptchaServiceProviderFactory implements CaptchaServiceProviderFactory {
    private Config.Scope config;

    @Override
    public CaptchaService create(KeycloakSession session) {
        GoogleRecaptchaService recaptchaService = new GoogleRecaptchaService(session);
        recaptchaService.setConfig(config);
        return recaptchaService;
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
        return "recaptcha";
    }
}
