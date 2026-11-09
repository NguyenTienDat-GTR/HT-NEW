package phucuong.ht.modules.parish.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class ParishResponse {

    private UUID id;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String createdByName;
    private String updatedByName;
    private Boolean status;
    // Trạng thái hiệu lực: false nếu chính giáo xứ hoặc giáo hạt/giáo phận cha đang bị tắt.
    private Boolean effectiveStatus;
    private String name;
    @JsonFormat(pattern = "MM-dd")
    private MonthDay patronSaintDay;
    private LocalDate establishmentDate;
    private String chaplain;
    private String phoneNumber;
    private String email;
    private UUID deaneryId;
    private String deaneryName;
    private UUID dioceseId;
    private String dioceseName;
    private String chapterName;
    private ParishUnitType unitType;
    private String unitTypeLabel;
    private UUID parentParishId;
    private String parentParishName;
    // Số giáo họ / giáo điểm trực thuộc (chỉ có ý nghĩa với giáo xứ).
    private Long subUnitCount;
    private Long leaderCount;
}
