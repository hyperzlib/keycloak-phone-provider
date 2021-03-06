package cc.coopersoft.keycloak.phone.providers.constants;

import java.time.Instant;
import java.util.Date;

public class MessageSendResult {
    private final int status;
    private String errorCode;
    private String errorMessage;
    private Date resendExpires;
    private Date expires;

    public MessageSendResult(int status){
        this.status = status;
    }

    public int getStatus(){
        return this.status;
    }

    public boolean ok(){
        return this.status > 0;
    }

    public MessageSendResult setError(String code, String message){
        this.errorCode = code;
        this.errorMessage = message;
        return this;
    }

    public String getErrorCode(){
        return this.errorCode;
    }

    public String getErrorMessage(){
        return this.errorMessage;
    }

    public MessageSendResult setResendExpires(Date resendExpires){
        this.resendExpires = resendExpires;
        return this;
    }

    public MessageSendResult setResendExpires(int resendExpires){
        Instant now = Instant.now();
        this.resendExpires = Date.from(now.plusSeconds(resendExpires));
        return this;
    }

    public Date getResendExpires(){
        return this.resendExpires;
    }

    public long getResendExpiresTime(){
        return this.resendExpires != null ? this.resendExpires.getTime() : 0;
    }

    public MessageSendResult setExpires(Date expires){
        this.expires = expires;
        return this;
    }

    public MessageSendResult setExpires(int expires){
        Instant now = Instant.now();
        this.expires = Date.from(now.plusSeconds(expires));
        return this;
    }

    public Date getExpires(){
        return this.expires;
    }

    public long getExpiresTime(){
        return this.expires != null ? this.expires.getTime() : 0;
    }
}
