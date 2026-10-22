package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class RolePermissionBulkRequest {

    @NotBlank
    private String roleCode;

    @NotEmpty
    private List<@NotBlank String> permissionCodes;


    private String conditions;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
