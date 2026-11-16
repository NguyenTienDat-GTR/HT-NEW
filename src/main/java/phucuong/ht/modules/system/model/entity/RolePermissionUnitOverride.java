package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.enums.RolePermissionOverrideType;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ngoại lệ của một vai trò tại một đơn vị (giáo phận / giáo hạt / giáo xứ) so với bộ quyền mặc định ở {@link RolePermission}.
 * GRANT cấp thêm quyền riêng; EXCLUDE thu hồi riêng quyền mặc định. Chỉ đúng một trong dioceseId, deaneryId, parishId được đặt.
 */
@Entity
@Table(name = "role_permission_unit_override")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class RolePermissionUnitOverride extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_code", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false, length = 20)
    private RolePermissionScopeLevel scopeLevel;

    @Column(name = "diocese_id")
    private UUID dioceseId;

    @Column(name = "deanery_id")
    private UUID deaneryId;

    @Column(name = "parish_id")
    private UUID parishId;

    /** Cột sinh sẵn ở DB: đơn vị cụ thể nhất của dòng. */
    @Column(name = "unit_id", insertable = false, updatable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_type", nullable = false, length = 20)
    private RolePermissionOverrideType overrideType;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

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
