package cc.coopersoft.keycloak.phone.providers.constants;

public enum TokenCodeType {
    VERIFY("verification"),
    OTP("authentication"),
    RESET("reset credential"),
    REGISTRATION("registration"),
    LOGIN("login");

    private String label;

    public String getLabel() {
        return label;
    }

    TokenCodeType(String label) {
        this.label  = label;
    }
}
