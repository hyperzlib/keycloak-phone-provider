package cc.coopersoft.keycloak.phone.utils;

import org.keycloak.authentication.forms.RegistrationPage;

public class PhoneConstants {
    public static final String FIELD_AREA_CODE = "areaCode";
    public static final String FIELD_PHONE_NUMBER = "phoneNumber";
    public static final String LEGACY_FIELD_AREA_CODE = "area_code";
    public static final String LEGACY_FIELD_PHONE_NUMBER = "phone_number";
    public static final String FIELD_VERIFICATION_CODE = "smsCode";
    public static final String FIELD_EMAIL = RegistrationPage.FIELD_EMAIL;
    public static final String FIELD_TOKEN_ID = "tokenId";
    public static final String FIELD_CREDENTIAL_TYPE = "credentialType";

    public static final String CREDENTIAL_TYPE_PHONE = "phone";
    public static final String CREDENTIAL_TYPE_EMAIL = "email";
    public static final String CREDENTIAL_TYPE_PASSWORD = "password";

    public static final String MISSING_PHONE_NUMBER = "requiredPhoneNumber";
    public static final String MISSING_VERIFY_CODE = "requireSmsCode";
    public static final String PHONE_EXISTS = "phoneNumberExists";
    public static final String SMS_CODE_MISMATCH = "smsCodeMismatch";

    public static final String USER_ATTRIBUTE_FIELD_PHONE_NUMBER = "phoneNumber";
}
