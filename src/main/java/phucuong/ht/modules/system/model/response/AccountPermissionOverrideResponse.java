package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.system.model.enums.PermissionEffect;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AccountPermissionOverrideResponse {
    private String id;
    private String username;
    private String permissionCode;
    private PermissionEffect effect;
    private String reason;
    private OffsetDateTime expiresAt;
    private Boolean status;
}
