package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.system.model.enums.PermissionEffect;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class AccountPermissionOverrideBulkRequest {

    @NotBlank
    private String username;

    @NotEmpty
    private List<@NotBlank String> permissionCodes;

    @NotNull
    private PermissionEffect effect;

    @NotBlank
    private String reason;

    private OffsetDateTime expiresAt;
}
