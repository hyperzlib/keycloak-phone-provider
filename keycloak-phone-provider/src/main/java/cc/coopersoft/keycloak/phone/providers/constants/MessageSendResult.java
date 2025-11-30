package cc.coopersoft.keycloak.phone.providers.constants;

import lombok.Getter;

import java.time.Instant;
import java.util.Date;

@Getter
public class MessageSendResult {
    private final int status;
    private String errorCode;
    private String errorMessage;
    private Date resendExpires;
    private Date expires;

    public MessageSendResult(int status) {
        this.status = status;
    }

    public boolean ok() {
        return this.status > 0;
    }

    public MessageSendResult setError(String code, String message) {
        this.errorCode = code;
        this.errorMessage = message;
        return this;
    }

    public MessageSendResult setResendExpires(Date resendExpires) {
        this.resendExpires = resendExpires;
        return this;
    }

    public MessageSendResult setResendExpires(int resendExpires) {
        Instant now = Instant.now();
        this.resendExpires = Date.from(now.plusSeconds(resendExpires));
        return this;
    }

    public long getResendExpiresTime() {
        return this.resendExpires != null ? this.resendExpires.getTime() : 0;
    }

    public MessageSendResult setExpires(Date expires) {
        this.expires = expires;
        return this;
    }

    public MessageSendResult setExpires(int expires) {
        Instant now = Instant.now();
        this.expires = Date.from(now.plusSeconds(expires));
        return this;
    }

    public long getExpiresTime() {
        return this.expires != null ? this.expires.getTime() : 0;
    }
}
