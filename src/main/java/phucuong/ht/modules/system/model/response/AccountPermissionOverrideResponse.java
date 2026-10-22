package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.system.model.enums.PermissionEffect;
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
    private PermissionEffect effect;
    private String reason;
    private OffsetDateTime expiresAt;
    private Boolean status;
    private PermissionValidity validity;
    private PermissionRevocationReason revocationReason;
    private OffsetDateTime revokedAt;
    private String revokedBy;
}
