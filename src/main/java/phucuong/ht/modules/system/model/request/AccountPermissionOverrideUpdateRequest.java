package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AccountPermissionOverrideUpdateRequest {

    private String username;

    private String permissionCode;


    private String reason;

    private OffsetDateTime expiresAt;
}
