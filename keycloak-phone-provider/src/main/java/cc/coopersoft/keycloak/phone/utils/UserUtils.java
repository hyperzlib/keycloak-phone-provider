package cc.coopersoft.keycloak.phone.utils;

import org.keycloak.models.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 *
 *
 */
public class UserUtils {

    private static UserModel singleUser(List<UserModel> users){
        if (users.isEmpty()) {
            return null;
        } else if (users.size() > 1){
            return users.stream()
                    .filter(u -> u.getAttributeStream("phoneNumberVerified")
                            .anyMatch("true"::equals))
                    .findFirst().orElse(null);
        } else {
            return users.get(0);
        }
    }

    private static UserModel singleUser(Stream<UserModel> users){
        return users.filter(u -> u.getAttributeStream("phoneNumberVerified")
                        .anyMatch("true"::equals)).findFirst().orElse(null);
    }

    public static UserModel findUserByPhone(UserProvider userProvider, RealmModel realm, PhoneNumber phoneNumber){
        Stream<UserModel> users = userProvider.searchForUserByUserAttributeStream(
                realm, "phoneNumber", phoneNumber.getFullPhoneNumber());
        return singleUser(users);
    }

    public static UserModel findUserByPhone(UserProvider userProvider, RealmModel realm,
                                            PhoneNumber phoneNumber, String notIs){
        Stream<UserModel> users = userProvider.searchForUserByUserAttributeStream(
                realm, "phoneNumber", phoneNumber.getFullPhoneNumber());
        return singleUser(users.filter(u -> !u.getId().equals(notIs)).collect(Collectors.toList()));
    }

    public static boolean isDuplicatePhoneAllowed(){
        //TODO isDuplicatePhoneAllowed
        return false;
    }
}
