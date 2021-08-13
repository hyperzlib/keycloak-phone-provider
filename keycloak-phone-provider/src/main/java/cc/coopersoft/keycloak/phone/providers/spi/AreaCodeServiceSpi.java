package cc.coopersoft.keycloak.phone.providers.spi;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class AreaCodeServiceSpi implements Spi {
    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "phoneAreaCodeProvider";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return AreaCodeService.class;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return AreaCodeServiceProviderFactory.class;
    }
}
