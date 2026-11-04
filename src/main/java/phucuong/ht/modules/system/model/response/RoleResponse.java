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
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String createdByName;
    private String updatedByName;
    /** Chỉ có giá trị khi liệt kê vai trò theo tài khoản: true nếu tài khoản đã được gán vai trò này. */
    private Boolean assigned;
}
