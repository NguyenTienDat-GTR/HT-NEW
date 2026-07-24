package phucuong.ht.modules.deanery.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class DeaneryResponse {

    private final UUID id;
    private final OffsetDateTime createdAt;
    private final String createdBy;
    private final OffsetDateTime updatedAt;
    private final String updatedBy;
    private final Boolean status;
    private final String name;
    private final LocalDate patronSaintDay;
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
