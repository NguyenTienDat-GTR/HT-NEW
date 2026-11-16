package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.enums.PermissionValidity;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RolePermissionResponse {
    private String id;
    // DEFAULT | UNIT_GRANT | UNIT_EXCLUDE
    private phucuong.ht.modules.system.model.enums.RolePermissionEntryType entryType;
    // DIOCESE | DEANERY | PARISH; null với dòng mặc định.
    private phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel scopeLevel;
    private java.util.UUID dioceseId;
    private String dioceseName;
    private java.util.UUID deaneryId;
    private String deaneryName;
    private java.util.UUID parishId;
    private String parishName;
    // Chuỗi hiển thị sẵn: "Tất cả giáo phận", "Giáo phận …", "Giáo hạt …", "Giáo xứ …".
    private String scopeName;
    private String roleCode;
    private String roleName;
    private String permissionCode;
    private String permissionName;
    private String conditions;
    private String assignedBy;
    private OffsetDateTime assignedAt;
    private OffsetDateTime expiresAt;
    private Boolean status;
    private PermissionValidity validity;
    // Người dùng hiện tại có được thu hồi dòng này không (theo phân cấp); null khi không tính theo người dùng.
    private Boolean revocable;
    private PermissionRevocationReason revocationReason;
    private OffsetDateTime revokedAt;
    private String revokedBy;
    private String assignedByName;
    private String revokedByName;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
