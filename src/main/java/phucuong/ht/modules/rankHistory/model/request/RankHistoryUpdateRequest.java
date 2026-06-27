package phucuong.ht.modules.rankHistory.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;

@Getter
@Setter
public class RankHistoryUpdateRequest {

    private String leaderCode;

    private LeaderLevel oldLevel;

    private LeaderLevel newLevel;

    private LocalDate promotionDate;

    private String note;
}
