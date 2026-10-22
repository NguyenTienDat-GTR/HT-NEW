package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AccountPermissionOverrideCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String permissionCode;


    private String reason;

    private OffsetDateTime expiresAt;
}
