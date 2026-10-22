package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class PermissionResponse {
    private String permissionCode;
    private String permissionName;
    private String moduleCode;
    private String resourceCode;
    private String actionCode;
    private String scopeCode;
    private String description;
    private Boolean isSystem;
    private Integer displayOrder;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String createdByName;
    private String updatedBy;
    private String updatedByName;
}
