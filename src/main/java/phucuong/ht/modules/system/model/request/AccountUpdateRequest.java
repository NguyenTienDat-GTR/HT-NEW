package phucuong.ht.modules.system.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUpdateRequest {

    private String password;

    private String leaderCode;

    private String authProvider;

    private String providerId;

    private String avatarUrl;
}
