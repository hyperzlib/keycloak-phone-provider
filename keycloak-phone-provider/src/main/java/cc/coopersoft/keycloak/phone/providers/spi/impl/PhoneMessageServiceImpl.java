package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.constants.MessageSendResult;
import cc.coopersoft.keycloak.phone.providers.spi.ConfigService;
import cc.coopersoft.keycloak.phone.providers.spi.TokenCodeService;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneMessageService;
import cc.coopersoft.keycloak.phone.utils.PhoneNumber;
import cc.coopersoft.keycloak.phone.utils.ServiceUtils;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import jakarta.ws.rs.ForbiddenException;
import org.keycloak.services.validation.Validation;

import java.util.Map;

public class PhoneMessageServiceImpl implements PhoneMessageService {
    private static final Logger logger = Logger.getLogger(PhoneMessageServiceImpl.class);
    private final KeycloakSession session;
    private final ConfigService config;
    private final int tokenExpiresIn;

    PhoneMessageServiceImpl(KeycloakSession session) {
        this.session = session;

        config = session.getProvider(ConfigService.class);
        this.tokenExpiresIn = config.getTokenExpires();
    }

    @Override
    public void close() {
    }

    private MessageSenderService getMessageSenderService(int areaCode) {
        Map<Integer, String> areaCodeSenderMap = config.getAreaCodeSenderMap();
        if (areaCodeSenderMap != null && areaCodeSenderMap.containsKey(areaCode)) {
            String senderServiceId = areaCodeSenderMap.get(areaCode);
            MessageSenderService senderForAreaCode = session.getProvider(MessageSenderService.class, senderServiceId);
            if (senderForAreaCode != null) {
                return senderForAreaCode;
            }
        }

        if (!Validation.isBlank(config.getDefaultSender())) {
            MessageSenderService defaultSender = session.getProvider(MessageSenderService.class, config.getDefaultSender());
            if (defaultSender != null) {
                return defaultSender;
            }
        }

        logger.warn("defaultSender not configured or not found, using fallback MessageSenderService");
        MessageSenderService fallbackSender = session.getProvider(MessageSenderService.class);
        if (fallbackSender == null) {
            throw new IllegalStateException("No MessageSenderService provider available");
        }

        return fallbackSender;
    }

    @Override
    public MessageSendResult sendTokenCode(PhoneNumber phoneNumber, String sourceAddr, TokenCodeType type, String kind) {
        if (!phoneNumber.isValid()) {
            return new MessageSendResult(-3).setError("INVALID_PHONE_NUMBER", "The phone number is invalid");
        }
        TokenCodeService tokenCodeService = ServiceUtils.getTokenCodeService(session);
        if (tokenCodeService.isAbusing(phoneNumber, type, sourceAddr)) {
            throw new ForbiddenException("You requested the maximum number of messages the last hour");
        }

        MessageSendResult result;

        if(!tokenCodeService.canResend(phoneNumber, type)) {
            TokenCodeRepresentation current = tokenCodeService.currentProcess(phoneNumber, type);
            result = new MessageSendResult(-2).setError("RATE_LIMIT", "Please wait for minutes.");
            if(current != null && current.getResendExpiresAt() != null){
                result.setResendExpires(current.getResendExpiresAt());
            }
            return result;
        }

        //remove old codes
        tokenCodeService.removeCode(phoneNumber, type);

        TokenCodeRepresentation token = TokenCodeRepresentation.forPhoneNumber(phoneNumber);

        ServiceUtils.GetMessageSenderServiceResult messageSenderServiceResult = ServiceUtils
                .getMessageSenderService(session, config, phoneNumber.getAreaCodeInt());
        MessageSenderService sender = messageSenderServiceResult.getService();
        try {
            result = sender.sendSmsMessage(type, phoneNumber, token.getCode(), tokenExpiresIn);
        } catch (MessageSendException e) {
            result = new MessageSendResult(-1).setError(e.getErrorCode(), e.getErrorMessage());
        }

        if (result.ok()) {
            if (result.getSmsCode() != null) {
                // Update the token code if the sender service generated a different code
                token.setCode(result.getSmsCode());
            }

            tokenCodeService.persistCode(token, type, result);

            logger.info(String.format("Sent %s code to %s over %s", type.getLabel(), phoneNumber.getFullPhoneNumber(),
                    messageSenderServiceResult.getId()));
        } else {
            logger.error(String.format("Message sending to %s failed with %s: %s",
                    phoneNumber.getFullPhoneNumber(), result.getErrorCode(), result.getErrorMessage()));
        }
        return result;
    }
}
