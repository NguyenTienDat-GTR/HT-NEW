package phucuong.ht.modules.leaderLevel.model.response;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LeaderLevelResponse {
    private String levelCode;
    private String levelName;
    // Bậc của cấp: số càng lớn cấp càng cao; các cấp cùng số là bằng cấp nhau.
    private Integer displayOrder;
    private Boolean isSystem;
    private String description;
    // Các cấp của bậc ngay trước: điều kiện để đạt cấp này (rỗng với bậc thấp nhất). Bậc có nhiều cấp thì đang ở một trong các cấp đó.
    private List<String> previousCodes;
    private List<String> previousNames;
    // Số loại khóa đang dùng cấp này (chỉ có ở danh sách quản lý).
    private Long courseTypeCount;
    private Boolean deletable;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
