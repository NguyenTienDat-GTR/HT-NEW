package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionActionUpdateRequest {

    private String actionName;

    private String description;

    private Integer displayOrder;
}
