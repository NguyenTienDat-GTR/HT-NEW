package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "account_permission_override",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_permission_override",
                        columnNames = {
                                "username",
                                "permission_code"
                        }
                )
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class AccountPermissionOverride extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "username", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_code", nullable = false)
    private Permission permission;


    @Column(name = "reason")
    private String reason;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 20)
    private PermissionRevocationReason revocationReason;
}
