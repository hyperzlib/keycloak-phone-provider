package cc.coopersoft.keycloak.phone.credential;

import com.google.auto.service.AutoService;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.models.KeycloakSession;

@AutoService(CredentialProviderFactory.class)
public class PhoneOtpCredentialProviderFactory implements CredentialProviderFactory<PhoneOtpCredentialProvider> {
    public final static String PROVIDER_ID = "sms-otp";

    @Override
    public CredentialProvider create(KeycloakSession session) {
        return new PhoneOtpCredentialProvider(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
