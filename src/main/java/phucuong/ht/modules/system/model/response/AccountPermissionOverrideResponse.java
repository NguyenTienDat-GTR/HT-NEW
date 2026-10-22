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
}
