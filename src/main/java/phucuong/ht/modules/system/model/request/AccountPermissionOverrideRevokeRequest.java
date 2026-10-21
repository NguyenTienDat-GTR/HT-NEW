package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AccountPermissionOverrideRevokeRequest {

    @NotBlank
    private String username;

    @NotEmpty
    private List<@NotBlank String> permissionCodes;
}
