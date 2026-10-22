package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.enums.PermissionValidity;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AccountPermissionOverrideResponse {
    private String id;
    private String username;
    private String permissionCode;
    private String permissionName;
    private String reason;
    private OffsetDateTime expiresAt;
    private Boolean status;
    private PermissionValidity validity;
    // Người dùng hiện tại có được thu hồi dòng này không (theo phân cấp); null khi không tính theo người dùng.
    private Boolean revocable;
    private PermissionRevocationReason revocationReason;
    private OffsetDateTime revokedAt;
    private String revokedBy;
    private String revokedByName;
    private String leaderFullName;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
