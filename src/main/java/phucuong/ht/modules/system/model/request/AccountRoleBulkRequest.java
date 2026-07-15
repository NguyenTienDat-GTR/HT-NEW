package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class AccountRoleBulkRequest {

    @NotBlank
    private String username;

    @NotEmpty
    private List<@NotBlank String> roleCodes;

    private String primaryRoleCode;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;
}
