package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.authentication.requiredactions.UpdatePhoneNumberRequiredAction;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialModel;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialProvider;
import cc.coopersoft.keycloak.phone.credential.PhoneOtpCredentialProviderFactory;
import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.jpa.TokenCode;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.utils.UserUtils;
import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TemporalType;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TokenCodeServiceImpl implements TokenCodeService {

    private static final Logger logger = Logger.getLogger(TokenCodeServiceImpl.class);
    private final KeycloakSession session;

    TokenCodeServiceImpl(KeycloakSession session) {
        this.session = session;
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
    public TokenCodeRepresentation currentProcess(String phoneNumber, TokenCodeType tokenCodeType) {

        try {
            TokenCode entity = getEntityManager()
                    .createNamedQuery("currentProcess", TokenCode.class)
                    .setParameter("realmId", getRealm().getId())
                    .setParameter("phoneNumber", phoneNumber)
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
    public void removeCode(String phoneNumber, TokenCodeType tokenCodeType) {
        try {
            EntityManager em = getEntityManager();
            List<TokenCode> entityList = em
                    .createNamedQuery("getAll", TokenCode.class)
                    .setParameter("realmId", getRealm().getId())
                    .setParameter("phoneNumber", phoneNumber)
                    .setParameter("type", tokenCodeType.name())
                    .getResultList();

            if(entityList.size() > 0) {
                for (TokenCode entity : entityList) {
                    em.remove(entity);
                }
                em.flush();
                em.clear();
            }
        } catch (NoResultException ignored) {

        }
    }

    @Override
    public boolean canResend(String phoneNumber, TokenCodeType tokenCodeType) {
        try {
            EntityManager em = getEntityManager();
            TokenCode entityList = em
                    .createNamedQuery("currentProcess", TokenCode.class)
                    .setParameter("realmId", getRealm().getId())
                    .setParameter("phoneNumber", phoneNumber)
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
    public boolean isAbusing(String phoneNumber, TokenCodeType tokenCodeType) {

        Date oneHourAgo = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));

        List<TokenCode> entities = getEntityManager()
                .createNamedQuery("processesSince", TokenCode.class)
                .setParameter("realmId", getRealm().getId())
                .setParameter("phoneNumber", phoneNumber)
                .setParameter("date", oneHourAgo, TemporalType.TIMESTAMP)
                .setParameter("type", tokenCodeType.name())
                .getResultList();

        return entities.size() > 3;
    }

    @Override
    public void persistCode(TokenCodeRepresentation tokenCode, TokenCodeType tokenCodeType, MessageSendResult sendResult) {
        TokenCode entity = new TokenCode();
        Instant now = Instant.now();

        entity.setId(tokenCode.getId());
        entity.setRealmId(getRealm().getId());
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
    public boolean validateCode(String phoneNumber, String code) {
        return validateCode(phoneNumber, code, TokenCodeType.VERIFY);
    }

    @Override
    public boolean validateCode(String phoneNumber, String code, TokenCodeType tokenCodeType) {
        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null) return false;
        if (!tokenCode.getCode().equals(code)) return false;

        removeCode(phoneNumber, tokenCodeType);
        return true;
    }

    @Override
    public boolean validateCode(UserModel user, String phoneNumber, String code) {
        return validateCode(user, phoneNumber, code, TokenCodeType.VERIFY);
    }

    @Override
    public boolean validateCode(UserModel user, String phoneNumber, String code, TokenCodeType tokenCodeType) {
        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null) return false;
        if (!tokenCode.getCode().equals(code)) return false;
        if (user.getAttributeStream("phoneNumber").noneMatch(p -> p.equals(phoneNumber))) return false;

        removeCode(phoneNumber, tokenCodeType);
        return true;
    }

    @Override
    public void setUserPhoneNumberByCode(UserModel user, String phoneNumber, String code){
        TokenCodeType tokenCodeType = TokenCodeType.VERIFY;
        logger.info(String.format("valid %s , phone: %s, code: %s", tokenCodeType, phoneNumber, code));

        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null)
            throw new BadRequestException(String.format("There is no valid ongoing %s process", tokenCodeType.getLabel()));

        if (!tokenCode.getCode().equals(code)) throw new ForbiddenException("Code does not match with expected value");

        logger.info(String.format("User %s correctly answered the %s code", user.getId(), tokenCodeType.getLabel()));

        removeCode(phoneNumber, tokenCodeType);
        session.users()
                .searchForUserByUserAttributeStream(session.getContext().getRealm(), "phoneNumber", phoneNumber)
                .filter(u -> !u.getId().equals(user.getId()))
                .forEach(u -> {
                    logger.info(String.format("User %s also has phone number %s. Un-verifying.", u.getId(), phoneNumber));
                    u.setSingleAttribute("phoneNumberVerified", "false");
                });

        user.setSingleAttribute("phoneNumberVerified", "true");
        user.setSingleAttribute("phoneNumber", phoneNumber);

        cleanUpAction(user);
    }

    @Override
    public void tokenValidated(UserModel user, String phoneNumber, String tokenCodeId) {
        if(!UserUtils.isDuplicatePhoneAllowed()) { //解绑重复的手机号
            session.users().searchForUserByUserAttributeStream(session.getContext().getRealm(), "phoneNumber",
                    phoneNumber).filter(u -> !u.getId().equals(user.getId()))
                    .forEach(u -> {
                        logger.info(String.format("User %s also has phone number %s. Un-verifying.", u.getId(), phoneNumber));
                        u.setSingleAttribute("phoneNumberVerified", "false");
                    });
        }

        user.setSingleAttribute("phoneNumberVerified", "true");
        user.setSingleAttribute("phoneNumber", phoneNumber);

        cleanUpAction(user);
    }

    @Override
    public void cleanUpAction(UserModel user) {
        user.removeRequiredAction(UpdatePhoneNumberRequiredAction.PROVIDER_ID);
        PhoneOtpCredentialProvider socp = (PhoneOtpCredentialProvider)
                session.getProvider(CredentialProvider.class, PhoneOtpCredentialProviderFactory.PROVIDER_ID);
        if (socp.isConfiguredFor(getRealm(), user, PhoneOtpCredentialModel.TYPE)) {
            Optional<CredentialModel> credentialOptional = session.userCredentialManager()
                    .getStoredCredentialsByTypeStream(getRealm(), user, PhoneOtpCredentialModel.TYPE).findFirst();
            if(credentialOptional.isPresent()) {
                CredentialModel credential = credentialOptional.get();
                credential.setCredentialData("{\"phoneNumber\":\"" + user.getFirstAttribute("phoneNumber") + "\"}");
                PhoneOtpCredentialModel credentialModel = PhoneOtpCredentialModel.createFromCredentialModel(credential);
                session.userCredentialManager().updateCredential(getRealm(), user, credentialModel);
            }
        }
    }

    @Override
    public Date getResendExpires(String phoneNumber, TokenCodeType tokenCodeType) {
        if (this.canResend(phoneNumber, tokenCodeType))
            throw new BadRequestException(String.format("Resend timeout in %s process for %s is finished.",
                    tokenCodeType.getLabel(), phoneNumber));

        TokenCodeRepresentation tokenCode = currentProcess(phoneNumber, tokenCodeType);
        if (tokenCode == null)
            throw new BadRequestException(String.format("There is no valid %s in process for %s",
                    tokenCodeType.getLabel(), phoneNumber));
        return tokenCode.getResendExpiresAt();
    }

    @Override
    public void close() {
    }
}
