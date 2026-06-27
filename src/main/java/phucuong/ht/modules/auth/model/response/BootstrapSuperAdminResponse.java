package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BootstrapSuperAdminResponse {

    private final String username;
    private final String roleCode;
    private final AuthTokenResponse tokens;
}
