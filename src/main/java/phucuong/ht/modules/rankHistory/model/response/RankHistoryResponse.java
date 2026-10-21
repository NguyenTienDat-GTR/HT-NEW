package phucuong.ht.modules.rankHistory.model.response;

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
}
