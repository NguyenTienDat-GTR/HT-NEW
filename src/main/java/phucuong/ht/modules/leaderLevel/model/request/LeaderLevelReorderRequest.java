package phucuong.ht.modules.leaderLevel.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeaderLevelReorderRequest {
    // Mã các cấp độ (không gồm cấp hệ thống NONE) theo thứ tự mới từ thấp đến cao.
    @NotEmpty
    private List<String> levelCodes;
}
