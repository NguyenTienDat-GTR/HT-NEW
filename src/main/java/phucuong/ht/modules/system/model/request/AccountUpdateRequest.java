package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountUpdateRequest {

    private String password;

    private UUID leaderId;

    private UUID dioceseId;

    private UUID deaneryId;

    private UUID parishId;

    /** Explicitly removes the direct organization scope when true. */
    private Boolean clearDirectScope;

    private String authProvider;

    private String providerId;

    private String avatarUrl;
}
