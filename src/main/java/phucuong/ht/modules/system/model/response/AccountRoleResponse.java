package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AccountRoleResponse {
    private String id;
    private String username;
    private String roleCode;
    private Boolean isPrimary;
    private String assignedBy;
    private OffsetDateTime assignedAt;
    private OffsetDateTime expiresAt;
    private Boolean status;
    private String roleName;
    private String leaderFullName;
    private String assignedByName;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
