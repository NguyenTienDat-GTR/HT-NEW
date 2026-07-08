package phucuong.ht.modules.rankHistory.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class RankHistoryCreateRequest {

    @NotBlank
    private UUID leaderId;

    private LeaderLevel oldLevel;

    @NotNull
    private LeaderLevel newLevel;

    private LocalDate promotionDate;

    private String note;
}
