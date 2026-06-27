package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionModuleCreateRequest {

    @NotBlank
    private String moduleCode;

    @NotBlank
    private String moduleName;

    private String description;

    private Integer displayOrder;
}
