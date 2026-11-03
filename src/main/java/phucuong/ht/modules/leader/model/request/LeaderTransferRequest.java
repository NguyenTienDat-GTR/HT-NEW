package phucuong.ht.modules.leader.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class LeaderTransferRequest {

    @NotNull(message = "Giáo xứ đích là bắt buộc")
    private UUID parishId;
}
