package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AccountResponse {
    private String username;
    private UUID leaderId;
    private UUID dioceseId;
    private UUID deaneryId;
    private UUID parishId;
    private String authProvider;
    private String providerId;
    private Boolean status;
}
