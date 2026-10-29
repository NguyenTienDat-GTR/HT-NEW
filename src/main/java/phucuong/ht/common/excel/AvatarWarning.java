package phucuong.ht.common.excel;

/** Lỗi/cảnh báo của riêng ảnh, không làm hỏng dữ liệu import. */
public record AvatarWarning(int row, String fullName, String fileName, String reason) {
}
