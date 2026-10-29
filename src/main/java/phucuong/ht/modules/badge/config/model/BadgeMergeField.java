package phucuong.ht.modules.badge.config.model;

import lombok.Getter;

// Bộ trường cố định dùng cho mail merge thẻ tham dự. Gọn hơn chứng nhận: không có người ký, mã / kết quả chứng nhận;
// có thêm tên đội. Nhãn tiếng Việt hiển thị cho người cấu hình.
@Getter
public enum BadgeMergeField {

    HOLY_NAME("holyName", "Tên thánh", "Huynh trưởng"),
    LEADER_NAME("leaderName", "Họ và tên", "Huynh trưởng"),
    // Một trường gộp: tên thánh + họ và tên.
    LEADER_FULL_NAME("leaderFullName", "Tên thánh, họ và tên", "Huynh trưởng"),
    LEADER_PARISH_NAME("leaderParishName", "Giáo xứ / giáo họ / giáo điểm", "Huynh trưởng"),
    LEADER_DEANERY_NAME("leaderDeaneryName", "Giáo hạt", "Huynh trưởng"),
    LEADER_DIOCESE_NAME("leaderDioceseName", "Giáo phận", "Huynh trưởng"),

    TEAM_NAME("teamName", "Tên đội", "Đội"),

    COURSE_NAME("courseName", "Tên khóa", "Khóa huấn luyện"),
    COURSE_CODE("courseCode", "Mã khóa", "Khóa huấn luyện"),
    HOST_YEAR("hostYear", "Năm tổ chức", "Khóa huấn luyện"),
    COURSE_START_DATE("courseStartDate", "Ngày bắt đầu", "Khóa huấn luyện"),
    COURSE_END_DATE("courseEndDate", "Ngày kết thúc", "Khóa huấn luyện"),
    COURSE_PERIOD("coursePeriod", "Thời gian tổ chức", "Khóa huấn luyện"),
    COURSE_LOCATION("courseLocation", "Địa điểm", "Khóa huấn luyện");

    private final String key;
    private final String label;
    private final String group;

    BadgeMergeField(String key, String label, String group) {
        this.key = key;
        this.label = label;
        this.group = group;
    }

    public static boolean isKnown(String key) {
        for (BadgeMergeField field : values()) {
            if (field.key.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
