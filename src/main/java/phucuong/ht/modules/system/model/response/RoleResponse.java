package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RoleResponse {
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean isSystem;
    private Integer displayOrder;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
