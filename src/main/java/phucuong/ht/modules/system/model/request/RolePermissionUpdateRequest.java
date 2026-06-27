package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.system.model.enums.PermissionEffect;

import java.time.OffsetDateTime;

@Getter
@Setter
public class RolePermissionUpdateRequest {

    private String roleCode;

    private String permissionCode;

    private PermissionEffect effect;

    private String conditions;

    private String assignedBy;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
