package phucuong.ht.common.excel.job;

/** Loại tác vụ nền Excel. fileBase là phần tên file xuất (không dấu); label hiển thị cho người dùng. */
public enum ExcelJobKind {
    EXPORT_DIOCESES("Xuất Excel giáo phận", "giao-phan", false),
    EXPORT_DEANERIES("Xuất Excel giáo hạt", "giao-hat", false),
    EXPORT_PARISHES("Xuất Excel giáo xứ", "giao-xu", false),
    EXPORT_LEADERS("Xuất Excel hồ sơ huynh trưởng", "ho-so-huynh-truong", false),
    EXPORT_COURSES("Xuất Excel khóa huấn luyện", "khoa-huan-luyen", false),
    EXPORT_PARTICIPATIONS("Xuất Excel đăng ký tham dự", "dang-ky-tham-du", false),
    EXPORT_PARTICIPANTS("Xuất Excel danh sách tham dự", "danh-sach-tham-du", false),
    EXPORT_CERTIFICATES("Xuất Excel chứng nhận", "chung-nhan", false),
    IMPORT_LEADERS("Import hồ sơ huynh trưởng", "", true),
    IMPORT_PARTICIPANTS("Import danh sách tham dự khóa huấn luyện", "", true);

    private final String label;
    private final String fileBase;
    private final boolean importJob;

    ExcelJobKind(String label, String fileBase, boolean importJob) {
        this.label = label;
        this.fileBase = fileBase;
        this.importJob = importJob;
    }

    public String label() {
        return label;
    }

    public String fileBase() {
        return fileBase;
    }

    public boolean isImport() {
        return importJob;
    }
}
