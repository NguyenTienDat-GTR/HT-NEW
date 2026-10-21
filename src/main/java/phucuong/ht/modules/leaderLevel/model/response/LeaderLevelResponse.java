package phucuong.ht.modules.leaderLevel.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LeaderLevelResponse {
    private String levelCode;
    private String levelName;
    // Thứ tự càng lớn cấp càng cao.
    private Integer displayOrder;
    private Boolean isSystem;
    private String description;
    // Cấp đứng ngay trước: điều kiện để đạt cấp này (null với cấp thấp nhất).
    private String previousCode;
    private String previousName;
    // Số nơi đang dùng cấp này (chỉ có ở danh sách quản lý).
    private Long leaderCount;
    private Long courseTypeCount;
    private Boolean deletable;
}
