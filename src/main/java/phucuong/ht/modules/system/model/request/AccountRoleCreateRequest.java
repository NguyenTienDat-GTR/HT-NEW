package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AccountRoleCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String roleCode;

    private Boolean isPrimary;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
