package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class RolePermissionCreateRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String permissionCode;


    private String conditions;

    private String assignedBy;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
