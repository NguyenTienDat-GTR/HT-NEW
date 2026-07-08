package phucuong.ht.modules.rankHistory.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class RankHistoryUpdateRequest {

    private UUID leaderId;

    private LeaderLevel oldLevel;

    private LeaderLevel newLevel;

    private LocalDate promotionDate;

    private String note;
}
