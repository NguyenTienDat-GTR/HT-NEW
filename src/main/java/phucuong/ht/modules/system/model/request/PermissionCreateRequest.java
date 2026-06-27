package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionCreateRequest {

    @NotBlank
    private String permissionCode;

    @NotBlank
    private String permissionName;

    private String moduleCode;

    private String resourceCode;

    private String actionCode;

    private String scopeCode;

    private String description;

    private Boolean isSystem;

    private Integer displayOrder;
}
