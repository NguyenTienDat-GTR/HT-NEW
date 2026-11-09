package phucuong.ht.modules.diocese.model.response;

import lombok.Builder;
import lombok.Getter;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class DioceseResponse {

    private final UUID id;
    private final OffsetDateTime createdAt;
    private final String createdBy;
    private final OffsetDateTime updatedAt;
    private final String updatedBy;
    private final String createdByName;
    private final String updatedByName;
    private final Boolean status;
    private final String name;
    @JsonFormat(pattern = "MM-dd")
    private final MonthDay patronSaintDay;
    private final LocalDate establishmentDate;
    private final String chaplain;
    private final String phoneNumber;
    private final String email;
    private final String unionName;
    private final Long deaneryCount;
    private final Long parishCount;
    private final Long leaderCount;
    private final String siteSubdomain;
    private final String siteBaseDomain;
    private final String siteUrl;
}
