package cc.coopersoft.keycloak.phone.providers.constants;

import lombok.Getter;

@Getter
public enum TokenCodeType {
    VERIFY("verification"),
    AUTH("authentication"),
    OTP("one-time password"),
    RESET("reset credential"),
    REGISTRATION("registration"),
    LOGIN("login");

    private final String label;

    TokenCodeType(String label) {
        this.label = label;
    }
}
