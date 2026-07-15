package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AuthenticatedUserResponse {

    private final String username;
    private final String leaderId;
    private final UUID dioceseId;
    private final UUID deaneryId;
    private final UUID parishId;
    private final List<String> roles;
    private final List<String> permissions;
    private final boolean mustChangePassword;
}
