package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class AccountPermissionOverrideBulkRequest {

    @NotBlank
    private String username;

    @NotEmpty
    private List<@NotBlank String> permissionCodes;


    @NotBlank
    private String reason;

    private OffsetDateTime expiresAt;
}
