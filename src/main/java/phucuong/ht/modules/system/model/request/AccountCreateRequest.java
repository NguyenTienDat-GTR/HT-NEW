package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String leaderCode;

    private String authProvider;

    private String providerId;

    private String avatarUrl;
}
