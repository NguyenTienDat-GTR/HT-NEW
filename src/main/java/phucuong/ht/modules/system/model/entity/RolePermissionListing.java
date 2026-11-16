package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.enums.RolePermissionEntryType;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Dạng chỉ đọc của view v_role_permission_listing: dòng mặc định và dòng ngoại lệ theo đơn vị, để dùng chung bộ tìm kiếm/lọc/sắp xếp. */
@Entity
@Immutable
@Subselect("select * from v_role_permission_listing")
@Synchronize({"role_permission", "role_permission_unit_override"})
@Getter
@Setter
public class RolePermissionListing extends BaseModel {

    @Id
    @Column(name = "id")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_code")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_code")
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type")
    private RolePermissionEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level")
    private RolePermissionScopeLevel scopeLevel;

    @Column(name = "diocese_id")
    private UUID dioceseId;

    @Column(name = "deanery_id")
    private UUID deaneryId;

    @Column(name = "parish_id")
    private UUID parishId;

    @Column(name = "conditions")
    private String conditions;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason")
    private PermissionRevocationReason revocationReason;
}
