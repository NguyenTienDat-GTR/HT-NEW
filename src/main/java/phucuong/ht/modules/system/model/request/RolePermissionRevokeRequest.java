package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RolePermissionRevokeRequest {

    @NotBlank
    private String roleCode;

    @NotEmpty
    private List<@NotBlank String> permissionCodes;

    private java.util.UUID dioceseId;

    private java.util.UUID deaneryId;

    private java.util.UUID parishId;
}
