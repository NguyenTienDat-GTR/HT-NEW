package phucuong.ht.modules.badge.export.model;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Yêu cầu xuất thẻ tham dự của một khóa. Tất cả trường đều tùy chọn:
// configId: cấu hình thẻ dùng (mặc định cấu hình chung gần nhất của khóa); participationIds: chỉ các hồ sơ này (mặc định cả danh sách tham dự);
// teamName: chỉ hồ sơ của đội này; includeBack: xuất cả mặt sau nếu cấu hình có (mặc định true);
// flipEdge: LONG | SHORT, cạnh lật khi in hai mặt (mặc định LONG).
public record BadgeExportRequest(UUID configId,
                                 @Size(max = 5000, message = "Mỗi lần xuất tối đa 5000 thẻ") List<String> participationIds,
                                 @Size(max = 150, message = "Tên đội tối đa 150 ký tự") String teamName,
                                 Boolean includeBack,
                                 @Size(max = 10) String flipEdge) {
}
