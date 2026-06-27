package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionResourceUpdateRequest {

    private String moduleCode;

    private String resourceName;

    private String description;

    private Integer displayOrder;
}
