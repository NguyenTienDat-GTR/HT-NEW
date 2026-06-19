package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "account_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_role",
                        columnNames = {
                                "username",
                                "role_code"
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
public class AccountRole extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "username", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}