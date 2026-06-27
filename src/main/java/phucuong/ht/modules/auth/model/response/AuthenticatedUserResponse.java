package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuthenticatedUserResponse {

    private final String username;
    private final String leaderId;
    private final List<String> roles;
    private final List<String> permissions;
}
