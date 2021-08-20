package cc.coopersoft.keycloak.phone.utils;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.keycloak.services.validation.Validation;

import javax.persistence.criteria.CriteriaBuilder;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Optional;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhoneNumber {
    public String areaCode;
    public String phoneNumber;

    public PhoneNumber(String fullPhoneNumber) {
        this.setFullPhoneNumber(fullPhoneNumber);
    }

    public PhoneNumber(MultivaluedMap<String, String> formData) {
        this.areaCode = Optional.ofNullable(formData.getFirst(PhoneConstants.FIELD_AREA_CODE))
                .orElse(formData.getFirst(PhoneConstants.LEGACY_FIELD_AREA_CODE));
        this.phoneNumber = Optional.ofNullable(formData.getFirst(PhoneConstants.FIELD_PHONE_NUMBER))
                .orElse(formData.getFirst(PhoneConstants.LEGACY_FIELD_PHONE_NUMBER));
    }

    public PhoneNumber(JsonNode jsonNode) {
        this.areaCode = jsonNode.get(PhoneConstants.FIELD_AREA_CODE)
                .asText(jsonNode.get(PhoneConstants.LEGACY_FIELD_AREA_CODE).asText());
        this.phoneNumber = jsonNode.get(PhoneConstants.FIELD_PHONE_NUMBER)
                .asText(jsonNode.get(PhoneConstants.LEGACY_FIELD_PHONE_NUMBER).asText());
    }

    public void setAreaCode(int areaCode) {
        this.areaCode = String.valueOf(areaCode);
    }

    public int getAreaCodeInt() {
        return Integer.parseInt(areaCode);
    }

    public String getFullPhoneNumber() {
        return "+" + areaCode + " " + phoneNumber;
    }

    public String getFullPhoneNumber(boolean noSpace) {
        if(noSpace) {
            return "+" + areaCode + phoneNumber;
        } else {
            return getFullPhoneNumber();
        }
    }

    public boolean setFullPhoneNumber(String fullPhoneNumber) {
        String[] chunk = fullPhoneNumber.replace("+", "").split(" ");
        if(chunk.length == 2){
            this.areaCode = chunk[0];
            this.phoneNumber = chunk[1];
            return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return Validation.isBlank(phoneNumber) || Validation.isBlank(areaCode);
    }
}
