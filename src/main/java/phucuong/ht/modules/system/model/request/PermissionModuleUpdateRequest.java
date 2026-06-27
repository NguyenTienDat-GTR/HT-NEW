package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionModuleUpdateRequest {

    private String moduleName;

    private String description;

    private Integer displayOrder;
}
