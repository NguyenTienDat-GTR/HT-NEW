package phucuong.ht.modules.system.model.response;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;
import java.util.List;

@Getter
@Builder
public class AccountResponse {
    private String username;
    private UUID leaderId;
    private String leaderHolyName;
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
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
