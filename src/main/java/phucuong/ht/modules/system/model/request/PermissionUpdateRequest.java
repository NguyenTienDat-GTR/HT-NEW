package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionUpdateRequest {

    private String permissionName;

    private String description;

    private Integer displayOrder;
}
