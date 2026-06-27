package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.system.model.enums.PermissionEffect;

import java.time.OffsetDateTime;

@Getter
@Setter
public class RolePermissionCreateRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String permissionCode;

    @NotNull
    private PermissionEffect effect;

    private String conditions;

    private String assignedBy;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
