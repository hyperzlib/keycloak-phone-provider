package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.authentication.requiredactions.UpdatePhoneNumberRequiredAction;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialModel;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialProvider;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialProviderFactory;
import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.jpa.TokenCodeEntity;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.ConfigUtils;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TemporalType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.validation.Validation;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TokenCodeServiceImpl implements TokenCodeService {

    private static final Logger logger = Logger.getLogger(TokenCodeServiceImpl.class);
    private final KeycloakSession session;
    private final Config.Scope config;

    TokenCodeServiceImpl(KeycloakSession session, Config.Scope config) {
        this.session = session;
        this.config = config;
        if (getRealm() == null) {
            throw new IllegalStateException("The service cannot accept a session without a realm in its context.");
        }
    }

    private EntityManager getEntityManager() {
        return session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    private RealmModel getRealm() {
        return session.getContext().getRealm();
    }

    @Override
    public TokenCodeRepresentation currentProcess(PhoneNumber phoneNumber, TokenCodeType tokenCodeType) {

        try {
            TokenCodeEntity entity = getEntityManager()
                    .createNamedQuery("currentProcess", TokenCodeEntity.class)
                    .setParameter("realmId", getRealm().getId())
                    .setParameter("areaCode", phoneNumber.getAreaCode())
                    .setParameter("phoneNumber", phoneNumber.getPhoneNumber())
                    .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                    .setParameter("type", tokenCodeType.name())
                    .getSingleResult();

            TokenCodeRepresentation tokenCodeRepresentation = new TokenCodeRepresentation();

            tokenCodeRepresentation.setId(entity.getId());
            tokenCodeRepresentation.setPhoneNumber(entity.getPhoneNumber());
            tokenCodeRepresentation.setCode(entity.getCode());
            tokenCodeRepresentation.setType(entity.getType());
            tokenCodeRepresentation.setCreatedAt(entity.getCreatedAt());
            tokenCodeRepresentation.setExpiresAt(entity.getExpiresAt());
            tokenCodeRepresentation.setResendExpiresAt(entity.getResendExpiresAt());
            tokenCodeRepresentation.setConfirmed(entity.getConfirmed());

            return tokenCodeRepresentation;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public void removeCode(PhoneNumber phoneNumber, TokenCodeType tokenCodeType) {
        try {
            EntityManager em = getEntityManager();
            List<TokenCodeEntity> entityList = em
                    .createNamedQuery("getAll", TokenCodeEntity.class)
                    .setParameter("realmId", getRealm().getId())
                    .setParameter("areaCode", phoneNumber.getAreaCode())
                    .setParameter("phoneNumber", phoneNumber.getPhoneNumber())
                    .setParameter("type", tokenCodeType.name())
                    .getResultList();

            if(entityList.size() > 0) {
                for (TokenCodeEntity entity : entityList) {
                    em.remove(entity);
                }
                em.flush();
                em.clear();
            }
        } catch (NoResultException ignored) {

        }
    }

    @Override
    public boolean canResend(PhoneNumber phoneNumber, TokenCodeType tokenCodeType) {
        try {
            EntityManager em = getEntityManager();
            TokenCodeEntity entityList = em
                    .createNamedQuery("currentProcess", TokenCodeEntity.class)
                    .setParameter("realmId", getRealm().getId())
                    .setParameter("areaCode", phoneNumber.getAreaCode())
                    .setParameter("phoneNumber", phoneNumber.getPhoneNumber())
                    .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                    .setParameter("type", tokenCodeType.name())
                    .getSingleResult();

            if(entityList != null) {
                Date resendExpiresAt = entityList.getResendExpiresAt();
                return (resendExpiresAt == null || resendExpiresAt.before(new Date()));
            } else {
                return true;
            }
        } catch (NoResultException ignored) {
            return true;
        }
    }

    @Override
    public boolean isAbusing(PhoneNumber phoneNumber, TokenCodeType tokenCodeType, String sourceAddr) {

        Date oneHourAgo = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));

        List<TokenCodeEntity> entities = getEntityManager()
                .createNamedQuery("processesSince", TokenCodeEntity.class)
                .setParameter("realmId", getRealm().getId())
                .setParameter("areaCode", phoneNumber.getAreaCode())
                .setParameter("phoneNumber", phoneNumber.getPhoneNumber())
                .setParameter("date", oneHourAgo, TemporalType.TIMESTAMP)
                .setParameter("type", tokenCodeType.name())
                .getResultList();

        return entities.size() > 3;
    }

    @Override
    public void persistCode(TokenCodeRepresentation tokenCode, TokenCodeType tokenCodeType, MessageSendResult sendResult) {
        TokenCodeEntity entity = new TokenCodeEntity();
        Instant now = Instant.now();

        entity.setId(tokenCode.getId());
        entity.setRealmId(getRealm().getId());
        entity.setAreaCode(tokenCode.getAreaCode());
        entity.setPhoneNumber(tokenCode.getPhoneNumber());
        entity.setCode(tokenCode.getCode());
        entity.setType(tokenCodeType.name());
        entity.setCreatedAt(Date.from(now));
        entity.setExpiresAt(sendResult.getExpires());
        entity.setResendExpiresAt(sendResult.getResendExpires());
        entity.setConfirmed(tokenCode.getConfirmed());

        getEntityManager().persist(entity);
    }

    @Override
    public boolean validateCode(PhoneNumber phoneNumber, String code) {
        return validateCode(phoneNumber, code, TokenCodeType.VERIFY);
    }

    @Override
    public boolean validateCode(PhoneNumber phoneNumber, String code, TokenCodeType tokenCodeType) {
        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null) return false;
        if (!tokenCode.getCode().equals(code)) return false;

        removeCode(phoneNumber, tokenCodeType);
        return true;
    }

    @Override
    public boolean validateCode(UserModel user, PhoneNumber phoneNumber, String code) {
        return validateCode(user, phoneNumber, code, TokenCodeType.VERIFY);
    }

    @Override
    public boolean validateCode(UserModel user, PhoneNumber phoneNumber, String code, TokenCodeType tokenCodeType) {
        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null) return false;
        if (!tokenCode.getCode().equals(code)) return false;
        if (user.getAttributeStream("phoneNumber")
                .noneMatch(p -> p.equals(phoneNumber.getFullPhoneNumber()))) return false;

        removeCode(phoneNumber, tokenCodeType);
        return true;
    }

    @Override
    public void setUserPhoneNumberByCode(UserModel user, PhoneNumber phoneNumber, String code){
        TokenCodeType tokenCodeType = TokenCodeType.VERIFY;
        logger.info(String.format("valid %s , phone: %s, code: %s", tokenCodeType, phoneNumber.getFullPhoneNumber(),
                code));

        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null)
            throw new BadRequestException(String.format("There is no valid ongoing %s process",
                    tokenCodeType.getLabel()));

        if (!tokenCode.getCode().equals(code))
            throw new ForbiddenException("Code does not match with expected value");

        logger.info(String.format("User %s correctly answered the %s code", user.getId(), tokenCodeType.getLabel()));

        removeCode(phoneNumber, tokenCodeType);

        tokenValidated(user, phoneNumber, tokenCode.getId(), false);
    }

    @Override
    public void tokenValidated(UserModel user, PhoneNumber phoneNumber, String tokenCodeId, boolean isOTP) {
        boolean updateUserPhoneNumber = !isOTP;
        String fullPhoneNumberString = phoneNumber.getFullPhoneNumber();

        // 检测是否为 OTP 验证，如果是，则检查用户的 OTP 凭据中的电话号码是否与提供的电话号码匹配
        if (isOTP) {
            updateUserPhoneNumber = PhoneOtpCredentialModel.getSmsOtpCredentialData(user)
                    .map(PhoneOtpCredentialModel.SmsOtpCredentialData::getPhoneNumber)
                    .map(fullPhoneNumberString::equals)
                    .orElse(false);
        }

        if (updateUserPhoneNumber) {
            if (!ConfigUtils.isDuplicatePhoneAllowed(session)) {
                session.users()
                        .searchForUserByUserAttributeStream(session.getContext().getRealm(),"phoneNumber", phoneNumber.toString())
                        .filter(u -> !u.getId().equals(user.getId()))
                        .forEach(u -> {
                            logger.info(String.format("User %s also has phone number %s. Un-verifying.", u.getId(), phoneNumber));
                            u.setSingleAttribute("phoneNumberVerified", "false");

                            u.addRequiredAction(UpdatePhoneNumberRequiredAction.PROVIDER_ID);

                            //remove otp Credentials
                            u.credentialManager()
                                    .getStoredCredentialsByTypeStream(PhoneOtpCredentialModel.TYPE)
                                    .filter(c -> {
                                        try {
                                            PhoneOtpCredentialModel.SmsOtpCredentialData credentialData =
                                                    JsonSerialization.readValue(c.getCredentialData(), PhoneOtpCredentialModel.SmsOtpCredentialData.class);
                                            if (Validation.isBlank(credentialData.getPhoneNumber())){
                                                return true;
                                            }
                                            return credentialData.getPhoneNumber().equals(user.getFirstAttribute("phoneNumber"));
                                        } catch (IOException e) {
                                            logger.warn("Unknown format Otp Credential", e);
                                            return true;
                                        }
                                    })
                                    .map(CredentialModel::getId)
                                    .collect(Collectors.toList())
                                    .forEach(id -> u.credentialManager().removeStoredCredentialById(id));
                        });
            }
            user.setSingleAttribute("phoneNumberVerified", "true");
            user.setSingleAttribute("phoneNumber", phoneNumber.toString());

            user.removeRequiredAction(UpdatePhoneNumberRequiredAction.PROVIDER_ID);
        }

        validateProcess(tokenCodeId, user);
    }

    @Override
    public void validateProcess(String tokenCodeId, UserModel user) {
        TokenCodeEntity entity = getEntityManager().find(TokenCodeEntity.class, tokenCodeId);
        entity.setConfirmed(true);
        entity.setByWhom(user.getId());
        getEntityManager().persist(entity);
    }

    @Override
    public Date getResendExpires(PhoneNumber phoneNumber, TokenCodeType tokenCodeType) {
        if (this.canResend(phoneNumber, tokenCodeType))
            throw new BadRequestException(String.format("Resend timeout in %s process for %s is finished.",
                    tokenCodeType.getLabel(), phoneNumber.getFullPhoneNumber()));

        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null)
            throw new BadRequestException(String.format("There is no valid %s in process for %s",
                    tokenCodeType.getLabel(), phoneNumber.getFullPhoneNumber()));
        return tokenCode.getResendExpiresAt();
    }

    @Override
    public void close() {
    }
}
