package phucuong.ht.modules.rankHistory.model.response;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class RankHistoryResponse {
    private Long id;
    private String oldLevel;
    private String newLevel;
    private LocalDate promotionDate;
    private String note;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
