package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionUpdateRequest {

    private String permissionName;

    private String moduleCode;

    private String resourceCode;

    private String actionCode;

    private String scopeCode;

    private String description;

    private Boolean isSystem;

    private Integer displayOrder;
}
