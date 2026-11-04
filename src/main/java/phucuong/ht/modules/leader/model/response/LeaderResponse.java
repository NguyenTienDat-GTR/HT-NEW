package phucuong.ht.modules.leader.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class LeaderResponse {

    private UUID id;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private Boolean status;
    private String holyName;
    private String fullName;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String email;
    private String phoneNumber;
    // URL ảnh đại diện đã ký (kích thước vừa) và ảnh thu nhỏ cho danh sách; hết hạn sau một thời gian.
    private String imageUrl;
    private String imageThumbUrl;
    private UUID imageAssetId;
    private LeaderGender gender;
    private String leaderLevel;
    private UUID deaneryId;
    private String deaneryName;
    private UUID parishId;
    private String parishName;
    // "Giáo họ X" / "Giáo điểm Y" khi huynh trưởng thuộc giáo họ / giáo điểm; rỗng nếu thuộc giáo xứ.
    private String subUnitName;
    private String createdByName;
    private String updatedByName;
}
