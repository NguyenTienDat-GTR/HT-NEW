package phucuong.ht.modules.rankHistory.model.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class RankHistoryUpdateRequest {

    private UUID leaderId;

    private String oldLevel;

    private String newLevel;

    private LocalDate promotionDate;

    private String note;
}
