package phucuong.ht.common.dto;

import java.util.List;

// Số huynh trưởng theo từng cấp độ của một giáo phận / giáo hạt / giáo xứ.
// Liệt kê đủ các cấp trong danh mục của giáo phận (kể cả cấp chưa có ai), theo thứ tự từ thấp lên cao.
public record UnitLeaderLevelStatsResponse(
        long total,
        List<Item> levels
) {
    public record Item(String levelCode, String levelName, int displayOrder, long leaderCount) {
    }
}
