package phucuong.ht.modules.parish.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ParishResponse {

    private UUID id;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private Boolean status;
    private String name;
    private LocalDate patronSaintDay;
    private LocalDate establishmentDate;
    private String chaplain;
    private String phoneNumber;
    private String email;
    private UUID deaneryId;
    private String deaneryName;
    private UUID dioceseId;
    private String dioceseName;
    private String chapterName;
}
