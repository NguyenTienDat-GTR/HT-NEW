package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AccountCreateRequest {

    private String roleCode;
    private List<String> roleCodes;
    private String primaryRoleCode;

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
