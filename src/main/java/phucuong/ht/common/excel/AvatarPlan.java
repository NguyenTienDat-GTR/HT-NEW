package phucuong.ht.common.excel;

import java.util.UUID;

/** Một ảnh sẽ được xử lý sau khi dữ liệu Excel đã commit. */
public record AvatarPlan(int row, UUID leaderId, String fullName, String fileName, UUID dioceseId) {
}
