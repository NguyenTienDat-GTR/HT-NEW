package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleCreateRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String roleName;

    private String description;

    private Boolean isSystem;

    private Integer displayOrder;
}
