package cc.coopersoft.keycloak.phone.utils;

import org.keycloak.models.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *
 *
 *
 */
public class UserUtils {
    private static Optional<UserModel> singleUser(List<UserModel> users) {
        if (users.isEmpty()) {
            return Optional.empty();
        } else if (users.size() > 1) {
            return users.stream()
                    .filter(u -> u.getAttributeStream("phoneNumberVerified")
                            .anyMatch("true"::equals))
                    .findFirst();
        } else {
            return Optional.ofNullable(users.get(0));
        }
    }

    private static Optional<UserModel> singleUser(Stream<UserModel> users) {
        return users.filter(u -> u.getAttributeStream("phoneNumberVerified")
                .anyMatch("true"::equals))
                .findFirst();
    }

    public static Optional<UserModel> findUserByPhone(KeycloakSession session, RealmModel realm,
            PhoneNumber phoneNumber) {
        UserProvider userProvider = session.users();
        Stream<UserModel> users = userProvider.searchForUserByUserAttributeStream(
                realm, "phoneNumber", phoneNumber.getFullPhoneNumber());
        return singleUser(users);
    }

    public static Optional<UserModel> findUserByPhone(KeycloakSession session, RealmModel realm,
            PhoneNumber phoneNumber, String notIs) {
        UserProvider userProvider = session.users();
        Stream<UserModel> users = userProvider.searchForUserByUserAttributeStream(
                realm, "phoneNumber", phoneNumber.getFullPhoneNumber());
        return singleUser(users.filter(u -> !u.getId().equals(notIs)));
    }
}
