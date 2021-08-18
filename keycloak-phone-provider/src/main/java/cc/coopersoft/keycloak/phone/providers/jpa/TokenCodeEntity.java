package cc.coopersoft.keycloak.phone.providers.jpa;

import lombok.*;

import javax.persistence.*;
import java.util.Date;
import java.util.Objects;

@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "PHONE_MESSAGE_TOKEN_CODE")
@NamedQueries({
        @NamedQuery(
                name = "currentProcess",
                query = "SELECT t FROM TokenCodeEntity t WHERE t.realmId = :realmId " +
                        "AND t.areaCode = :areaCode AND t.phoneNumber = :phoneNumber " +
                        "AND t.expiresAt >= :now AND t.type = :type"
        ),
        @NamedQuery(
                name = "getAll",
                query = "SELECT t FROM TokenCodeEntity t WHERE t.realmId = :realmId " +
                        "AND t.areaCode = :areaCode AND t.phoneNumber = :phoneNumber " +
                        "AND t.type = :type"
        ),
        @NamedQuery(
                name = "processesSince",
                query = "SELECT t FROM TokenCodeEntity t WHERE t.realmId = :realmId " +
                        "AND t.areaCode = :areaCode AND t.phoneNumber = :phoneNumber " +
                        "AND t.createdAt >= :date AND t.type = :type"
        )
})
public class TokenCodeEntity {
    @Id
    @Column(name = "ID")
    private String id;

    @Column(name = "REALM_ID", nullable = false)
    private String realmId;

    @Column(name = "AREA_CODE", nullable = false)
    private String areaCode;

    @Column(name = "PHONE_NUMBER", nullable = false)
    private String phoneNumber;

    @Column(name = "TYPE", nullable = false)
    private String type;

    @Column(name = "CODE", nullable = false)
    private String code;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATED_AT", nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "RESEND_EXPIRES_AT", nullable = false)
    private Date resendExpiresAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "EXPIRES_AT", nullable = false)
    private Date expiresAt;

    @Column(name = "CONFIRMED", nullable = false)
    private Boolean confirmed;

    @Column(name = "BY_WHOM")
    private String byWhom;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof TokenCodeEntity)) return false;
        TokenCodeEntity that = (TokenCodeEntity) o;

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
