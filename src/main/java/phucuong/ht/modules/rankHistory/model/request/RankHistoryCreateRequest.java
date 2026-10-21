package phucuong.ht.modules.rankHistory.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class RankHistoryCreateRequest {

    @NotBlank
    private UUID leaderId;

    private String oldLevel;

    @NotNull
    private String newLevel;

    private LocalDate promotionDate;

    private String note;
}
