package phucuong.ht.modules.leaderLevel.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaderLevelRequest {
    // Mã cấp độ (chữ hoa, số, gạch dưới); chỉ dùng khi tạo mới, không đổi được sau đó.
    private String levelCode;
    private String levelName;
    private String description;
}
