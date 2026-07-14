package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountCreateRequest {

    @NotBlank
    private String roleCode;

    /**
     * Leader linked to every account except the leaderless ADMIN_DIOCESE account.
     */
    private UUID leaderId;
    /**
     * Required for every ADMIN_DIOCESE account, whether or not it links to a leader.
     */
    private UUID dioceseId;

    /**
     * Required only for a leaderless ADMIN_DIOCESE account and is not persisted.
     */
    @Email
    @Size(max = 100)
    private String credentialEmail;
}
