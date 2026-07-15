package phucuong.ht.modules.rankHistory.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;

@Getter
@Builder
public class RankHistoryResponse {
    private Long id;
    private LeaderLevel oldLevel;
    private LeaderLevel newLevel;
    private LocalDate promotionDate;
    private String note;
}
