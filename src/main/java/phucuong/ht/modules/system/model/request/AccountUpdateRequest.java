package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountUpdateRequest {

    /** Only non-ADMIN_DIOCESE accounts may change their linked leader. */
    private UUID leaderId;

    /** Only the leaderless ADMIN_DIOCESE account may change its direct diocese. */
    private UUID dioceseId;
}
