package phucuong.ht.modules.badge.config.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** Xuất PDF xem trước từ cấu hình thẻ đang soạn (kể cả chưa lưu), điền bằng dữ liệu mẫu do trình soạn gửi lên. */
@Getter
@Setter
public class BadgePreviewPdfRequest {

    // Nội dung cấu hình đang soạn, cùng cấu trúc khi lưu.
    private BadgeConfigUpsertRequest config;
    // Giá trị mẫu theo mã trường (leaderFullName, teamName...). Trường thiếu thì để trống.
    private Map<String, String> values;
}
