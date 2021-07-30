package cc.coopersoft.keycloak.phone.providers.representations;

import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.security.SecureRandom;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenCodeRepresentation {
    private String id;
    private String areaCode;
    private String phoneNumber;
    private String code;
    private String type;
    private Date createdAt;
    private Date expiresAt;
    private Date resendExpiresAt;
    private Boolean confirmed;

    public static TokenCodeRepresentation forPhoneNumber(PhoneNumber phoneNumber) {
        TokenCodeRepresentation tokenCode = new TokenCodeRepresentation();

        tokenCode.id = KeycloakModelUtils.generateId();
        tokenCode.areaCode = phoneNumber.getAreaCode();
        tokenCode.phoneNumber = phoneNumber.getPhoneNumber();
        tokenCode.code = generateTokenCode();
        tokenCode.confirmed = false;

        return tokenCode;
    }

    private static String generateTokenCode() {
        SecureRandom secureRandom = new SecureRandom();
        Integer code = secureRandom.nextInt(999_999);
        return String.format("%06d", code);
    }
}
