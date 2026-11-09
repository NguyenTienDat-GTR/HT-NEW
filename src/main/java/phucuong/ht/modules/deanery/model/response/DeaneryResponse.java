package phucuong.ht.modules.deanery.model.response;

import lombok.Builder;
import lombok.Getter;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class DeaneryResponse {

    private final UUID id;
    private final OffsetDateTime createdAt;
    private final String createdBy;
    private final OffsetDateTime updatedAt;
    private final String updatedBy;
    private final String createdByName;
    private final String updatedByName;
    private final Boolean status;
    // Trạng thái hiệu lực: false nếu chính giáo hạt hoặc giáo phận cha đang bị tắt.
    private final Boolean effectiveStatus;
    private final String name;
    @JsonFormat(pattern = "MM-dd")
    private final MonthDay patronSaintDay;
    private final LocalDate establishmentDate;
    private final String chaplain;
    private final String phoneNumber;
    private final String email;
    private final String associationName;
    private final UUID dioceseId;
    private final String dioceseName;
    private final Long parishCount;
    private final Long leaderCount;
}
