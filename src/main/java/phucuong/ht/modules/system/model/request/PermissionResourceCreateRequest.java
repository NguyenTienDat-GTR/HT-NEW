package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionResourceCreateRequest {

    @NotBlank
    private String resourceCode;

    @NotBlank
    private String moduleCode;

    @NotBlank
    private String resourceName;

    private String description;

    private Integer displayOrder;
}
