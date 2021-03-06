package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneMessageService;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;

import javax.ws.rs.ForbiddenException;

public class PhoneMessageServiceImpl implements PhoneMessageService {

    private static final Logger logger = Logger.getLogger(PhoneMessageServiceImpl.class);
    private final KeycloakSession session;
    private final String service;
    private final int tokenExpiresIn;

    PhoneMessageServiceImpl(KeycloakSession session, Scope config) {
        this.session = session;

        this.service = session.listProviderIds(MessageSenderService.class)
                .stream().filter(s -> s.equals(config.get("service")))
                .findFirst().orElse(
                        session.listProviderIds(MessageSenderService.class)
                                .stream().findFirst().orElse("")
                );
        this.tokenExpiresIn = config.getInt("tokenExpiresIn", 60);
    }

    @Override
    public void close() {
    }


    private TokenCodeService getTokenCodeService() {
        return session.getProvider(TokenCodeService.class);
    }

    @Override
    public MessageSendResult sendTokenCode(String phoneNumber, TokenCodeType type){
        if (getTokenCodeService().isAbusing(phoneNumber, type)) {
            throw new ForbiddenException("You requested the maximum number of messages the last hour");
        }

        MessageSendResult result;

        if(!getTokenCodeService().canResend(phoneNumber, type)){
            TokenCodeRepresentation current = getTokenCodeService().currentProcess(phoneNumber, type);
            result = new MessageSendResult(-2).setError("RATE_LIMIT", "Please wait for minutes.");
            if(current != null && current.getResendExpiresAt() != null){
                result.setResendExpires(current.getResendExpiresAt());
            }
            return result;
        }

        //remove old codes
        getTokenCodeService().removeCode(phoneNumber, type);

        TokenCodeRepresentation token = TokenCodeRepresentation.forPhoneNumber(phoneNumber);

        try {
            result = session.getProvider(MessageSenderService.class, service)
                    .sendSmsMessage(type,phoneNumber, token.getCode(), tokenExpiresIn);
            getTokenCodeService().persistCode(token, type, result);
        } catch (MessageSendException e) {
            result = new MessageSendResult(-1).setError(e.getErrorCode(), e.getErrorMessage());
        }

        if(result.ok()){
            logger.info(String.format("Sent %s code to %s over %s",type.getLabel(), phoneNumber, service));
        } else {
            logger.error(String.format("Message sending to %s failed with %s: %s",
                    phoneNumber, result.getErrorCode(), result.getErrorMessage()));
        }
        return result;
    }
}
