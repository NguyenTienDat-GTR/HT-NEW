package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.system.model.enums.PermissionEffect;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RolePermissionResponse {
    private String id;
    private String roleCode;
    private String permissionCode;
    private PermissionEffect effect;
    private String conditions;
    private String assignedBy;
    private OffsetDateTime assignedAt;
    private OffsetDateTime expiresAt;
    private Boolean status;
}
