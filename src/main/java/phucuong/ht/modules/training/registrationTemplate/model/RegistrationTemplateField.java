package phucuong.ht.modules.training.registrationTemplate.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Trường gắn được vào mẫu danh sách đăng ký. CELL: chèn vào nội dung một ô dưới dạng {{key}} (tiêu đề, kính gửi, xứ đoàn...);
 * COLUMN: giá trị của từng người trong bảng dữ liệu. Nhãn tiếng Việt hiển thị cho người cấu hình.
 */
@Getter
public enum RegistrationTemplateField {

    UNIT_LEVEL_LABEL("capDonVi", "Cấp đơn vị", "Cấp đơn vị đoàn (liên đoàn / hiệp đoàn / xứ đoàn)", Kind.CELL),
    UNIT_LEVEL_NAME("tenDonVi", "Tên đơn vị", "Cấp đơn vị đoàn (liên đoàn / hiệp đoàn / xứ đoàn)", Kind.CELL),
    UNIT_LEVEL_FULL("capVaTenDonVi", "Cấp + tên đơn vị", "Cấp đơn vị đoàn (liên đoàn / hiệp đoàn / xứ đoàn)", Kind.CELL),
    CHURCH_LEVEL_LABEL("capGiaoHoi", "Cấp giáo hội", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    CHURCH_LEVEL_NAME("tenGiaoHoi", "Tên đơn vị giáo hội", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    CHURCH_LEVEL_FULL("capVaTenGiaoHoi", "Cấp + tên giáo hội", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    DIOCESE_FULL("giaoPhanVaTen", "Giáo phận + tên", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    DEANERY_FULL("giaoHatVaTen", "Giáo hạt + tên", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    PARISH_FULL("giaoXuVaTen", "Giáo xứ + tên", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    SUB_UNIT_FULL("giaoHoDiemVaTen", "Giáo họ / giáo điểm + tên", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    SUB_UNIT_KIND("loaiGiaoHoDiem", "Loại giáo họ / giáo điểm", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    SUB_UNIT_NAME("tenGiaoHoDiem", "Tên giáo họ / giáo điểm", "Đơn vị giáo hội (giáo phận / giáo hạt / giáo xứ)", Kind.CELL),
    DIOCESE_NAME("tenGiaoPhan", "Tên giáo phận", "Giáo phận", Kind.CELL),
    UNION_NAME("tenLienDoan", "Tên liên đoàn", "Giáo phận", Kind.CELL),
    DEANERY_NAME("tenGiaoHat", "Tên giáo hạt", "Nhóm đang xuất", Kind.CELL),
    ASSOCIATION_NAME("tenHiepDoan", "Tên hiệp đoàn", "Nhóm đang xuất", Kind.CELL),
    PARISH_NAME("tenGiaoXu", "Tên giáo xứ", "Nhóm đang xuất", Kind.CELL),
    CHAPTER_NAME("tenXuDoan", "Tên xứ đoàn", "Nhóm đang xuất", Kind.CELL),
    PARISH_CHAPLAIN("chaTuyenUy", "Cha tuyên úy", "Nhóm đang xuất", Kind.CELL),
    GROUP_NAME("tenNhom", "Tên nhóm", "Nhóm đang xuất", Kind.CELL),
    PARTICIPANT_COUNT("soNguoi", "Số người trong danh sách", "Nhóm đang xuất", Kind.CELL),

    COURSE_NAME("tenKhoa", "Tên khóa", "Khóa huấn luyện", Kind.CELL),
    COURSE_CODE("maKhoa", "Mã khóa", "Khóa huấn luyện", Kind.CELL),
    COURSE_TYPE("loaiKhoa", "Loại khóa", "Khóa huấn luyện", Kind.CELL),
    HOST_YEAR("namToChuc", "Năm tổ chức", "Khóa huấn luyện", Kind.CELL),
    COURSE_LOCATION("diaDiem", "Địa điểm", "Khóa huấn luyện", Kind.CELL),
    COURSE_START_DATE("ngayBatDau", "Ngày bắt đầu", "Khóa huấn luyện", Kind.CELL),
    COURSE_END_DATE("ngayKetThuc", "Ngày kết thúc", "Khóa huấn luyện", Kind.CELL),
    COURSE_ORGANIZER("donViToChuc", "Đơn vị tổ chức", "Khóa huấn luyện", Kind.CELL),

    EXPORT_DATE("ngayXuat", "Ngày xuất", "Ngày xuất file", Kind.CELL),
    EXPORT_DAY("ngay", "Ngày", "Ngày xuất file", Kind.CELL),
    EXPORT_MONTH("thang", "Tháng", "Ngày xuất file", Kind.CELL),
    EXPORT_YEAR("nam", "Năm", "Ngày xuất file", Kind.CELL),

    INDEX("stt", "STT", "Bảng dữ liệu", Kind.COLUMN),
    HOLY_NAME("tenThanh", "Tên thánh", "Bảng dữ liệu", Kind.COLUMN),
    FULL_NAME("hoVaTen", "Họ và tên", "Bảng dữ liệu", Kind.COLUMN),
    HOLY_AND_FULL_NAME("tenThanhHoVaTen", "Tên thánh, họ và tên", "Bảng dữ liệu", Kind.COLUMN),
    BIRTH_DATE("ngaySinh", "Ngày sinh", "Bảng dữ liệu", Kind.COLUMN),
    BIRTH_YEAR("namSinh", "Năm sinh", "Bảng dữ liệu", Kind.COLUMN),
    GENDER("gioiTinh", "Giới tính", "Bảng dữ liệu", Kind.COLUMN),
    PHONE("soDienThoai", "Số điện thoại", "Bảng dữ liệu", Kind.COLUMN),
    EMAIL("email", "Email", "Bảng dữ liệu", Kind.COLUMN),
    ROW_PARISH("giaoXu", "Tên giáo xứ", "Bảng dữ liệu", Kind.COLUMN),
    ROW_SUB_UNIT("giaoHoDiem", "Giáo họ / giáo điểm + tên", "Bảng dữ liệu", Kind.COLUMN),
    ROW_SUB_UNIT_KIND("loaiHoDiem", "Loại giáo họ / giáo điểm", "Bảng dữ liệu", Kind.COLUMN),
    ROW_SUB_UNIT_NAME("tenHoDiem", "Tên giáo họ / giáo điểm", "Bảng dữ liệu", Kind.COLUMN),
    ROW_DEANERY("giaoHat", "Giáo hạt", "Bảng dữ liệu", Kind.COLUMN),
    LEADER_LEVEL("capHienTai", "Cấp hiện tại", "Bảng dữ liệu", Kind.COLUMN),
    LEVEL_ACHIEVED_YEAR("namDatCap", "Năm đạt cấp hiện tại", "Bảng dữ liệu", Kind.COLUMN),
    REGISTRATION_DATE("ngayDangKy", "Ngày đăng ký", "Bảng dữ liệu", Kind.COLUMN),
    APPROVAL_STATUS("trangThaiDuyet", "Trạng thái duyệt", "Bảng dữ liệu", Kind.COLUMN),
    TEAM_NAME("doi", "Đội", "Bảng dữ liệu", Kind.COLUMN),
    REGISTRATION_NOTE("ghiChu", "Ghi chú đăng ký", "Bảng dữ liệu", Kind.COLUMN);

    public enum Kind { CELL, COLUMN }

    private final String key;
    private final String label;
    private final String group;
    private final Kind kind;

    RegistrationTemplateField(String key, String label, String group, Kind kind) {
        this.key = key;
        this.label = label;
        this.group = group;
        this.kind = kind;
    }

    public static Optional<RegistrationTemplateField> byKey(String key) {
        return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
    }
}
