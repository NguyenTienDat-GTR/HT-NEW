package phucuong.ht.common.excel;

/** Một lỗi trong file import: sheet, số dòng (theo Excel, dòng tiêu đề là 1), cột và nội dung lỗi. */
public record ImportError(String sheet, int row, String column, String message) {
}
