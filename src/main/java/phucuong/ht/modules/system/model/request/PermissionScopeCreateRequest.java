package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionScopeCreateRequest {

    @NotBlank
    private String scopeCode;

    @NotBlank
    private String scopeName;

    private String description;

    private Integer displayOrder;
}
