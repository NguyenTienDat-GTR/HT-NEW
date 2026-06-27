package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AccountRoleUpdateRequest {

    private String username;

    private String roleCode;

    private Boolean isPrimary;

    private String assignedBy;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
