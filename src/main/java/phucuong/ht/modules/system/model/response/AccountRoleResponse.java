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
}
