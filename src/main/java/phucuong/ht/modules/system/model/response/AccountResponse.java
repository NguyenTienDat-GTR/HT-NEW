package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;
import java.util.List;

@Getter
@Builder
public class AccountResponse {
    private String username;
    private UUID leaderId;
    private String leaderFullName;
    private String primaryRoleCode;
    private String primaryRoleName;
    private List<String> roleCodes;
    private List<String> roleNames;
    private List<String> secondaryRoleNames;
    private UUID dioceseId;
    private String dioceseName;
    private UUID deaneryId;
    private String deaneryName;
    private UUID parishId;
    private String parishName;
    private String authProvider;
    private String providerId;
    private Boolean status;
}
