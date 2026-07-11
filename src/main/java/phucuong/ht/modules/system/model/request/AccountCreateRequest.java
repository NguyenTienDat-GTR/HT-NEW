package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String roleCode;

    private UUID leaderId;

    private UUID dioceseId;

    private UUID deaneryId;

    private UUID parishId;

    private String authProvider;

    private String providerId;

    private String avatarUrl;
}
