package phucuong.ht.modules.leaderLevel.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeaderLevelReorderRequest {
    // Các bậc từ thấp đến cao (không gồm cấp hệ thống NONE); mỗi bậc là danh sách các cấp bằng nhau.
    // Ví dụ [["DU_TRUONG"], ["HT_I", "HT_I_B"], ["HT_II"]]: HT_I và HT_I_B bằng cấp nhau, cùng cao hơn Dự trưởng.
    @NotEmpty
    private List<List<String>> tiers;

    // true = bậc thấp nhất (tiers[0]) bằng cấp với cấp khởi đầu (cấp hệ thống NONE), tức cùng bậc 0 và không cần cấp trước.
    private Boolean firstTierBase;
}
