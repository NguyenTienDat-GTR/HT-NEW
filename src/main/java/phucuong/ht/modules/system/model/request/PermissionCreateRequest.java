package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionCreateRequest {

    @NotBlank
    private String permissionName;

    @NotBlank
    private String moduleCode;

    @NotBlank
    private String resourceCode;

    @NotBlank
    private String actionCode;

    @NotBlank
    private String scopeCode;

    private String description;

    private Integer displayOrder;
}
