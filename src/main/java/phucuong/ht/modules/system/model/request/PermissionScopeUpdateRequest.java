package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionScopeUpdateRequest {

    private String scopeName;

    private String description;

    private Integer displayOrder;
}
