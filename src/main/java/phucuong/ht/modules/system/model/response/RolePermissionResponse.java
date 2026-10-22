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
    private PermissionRevocationReason revocationReason;
    private OffsetDateTime revokedAt;
    private String revokedBy;
}
