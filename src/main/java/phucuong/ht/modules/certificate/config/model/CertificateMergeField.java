package phucuong.ht.modules.certificate.config.model;

import lombok.Getter;

// Bộ trường cố định dùng cho mail merge chứng nhận. Nhãn tiếng Việt hiển thị cho người cấu hình.
@Getter
public enum CertificateMergeField {

    SIGNER_NAME("signerName", "Tên người ký", "Người ký"),
    SIGNER_TITLE("signerTitle", "Chức danh người ký", "Người ký"),

    // Một trường gộp: tên thánh + họ và tên.
    LEADER_FULL_NAME("leaderFullName", "Tên thánh, họ và tên", "Huynh trưởng"),
    LEADER_BIRTH_DATE("leaderBirthDate", "Ngày sinh", "Huynh trưởng"),
    LEADER_GENDER("leaderGender", "Giới tính", "Huynh trưởng"),
    LEADER_PARISH_NAME("leaderParishName", "Giáo xứ", "Huynh trưởng"),
    LEADER_DEANERY_NAME("leaderDeaneryName", "Giáo hạt", "Huynh trưởng"),
    LEADER_DIOCESE_NAME("leaderDioceseName", "Giáo phận", "Huynh trưởng"),
    LEADER_LEVEL("leaderLevel", "Cấp hiện tại", "Huynh trưởng"),

    COURSE_NAME("courseName", "Tên khóa", "Khóa học"),
    COURSE_CODE("courseCode", "Mã khóa", "Khóa học"),
    HOST_YEAR("hostYear", "Năm tổ chức", "Khóa học"),
    RESULTING_LEVEL("resultingLevel", "Cấp độ đạt được", "Khóa học"),

    CERTIFICATE_CODE("certificateCode", "Mã chứng nhận", "Chứng nhận"),
    CERTIFICATE_NAME("certificateName", "Tên chứng nhận", "Chứng nhận"),
    RESULT("result", "Kết quả", "Chứng nhận"),
    ISSUE_DATE("issueDate", "Ngày cấp", "Chứng nhận");

    private final String key;
    private final String label;
    private final String group;

    CertificateMergeField(String key, String label, String group) {
        this.key = key;
        this.label = label;
        this.group = group;
    }
}
