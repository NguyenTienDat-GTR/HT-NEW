package phucuong.ht.modules.system.model.response;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;
import java.util.List;

@Getter
@Builder(toBuilder = true)
public class AccountResponse {
    private String username;
    private UUID leaderId;
    private String leaderHolyName;
    private String leaderFullName;
    private String leaderLevel;
    /** Giáo xứ của huynh trưởng (giáo xứ mẹ nếu thuộc giáo họ / giáo điểm). */
    private String leaderParishName;
    /** "Giáo họ X" / "Giáo điểm Y"; rỗng nếu huynh trưởng thuộc giáo xứ. */
    private String leaderSubUnitName;
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
    /** Email liên hệ/nhận thông tin; chỉ có giá trị khi người xem là SUPER_ADMIN. */
    private String email;

    public AccountResponse withEmail(String email) {
        return toBuilder().email(email).build();
    }
}
