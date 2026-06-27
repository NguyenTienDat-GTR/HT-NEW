package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionActionCreateRequest {

    @NotBlank
    private String actionCode;

    @NotBlank
    private String actionName;

    private String description;

    private Integer displayOrder;
}
