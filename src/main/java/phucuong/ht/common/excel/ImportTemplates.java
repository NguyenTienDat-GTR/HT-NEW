package phucuong.ht.common.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import phucuong.ht.modules.deanery.service.DeaneryImportService;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.parish.service.ParishImportService;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

/**
 * File Excel mẫu để người dùng tự tạo file import. Tiêu đề cột lấy từ đúng hằng số mà bộ đọc file dùng nên mẫu luôn khớp với cách import đọc cột.
 * Sheet đầu tiên là sheet dữ liệu (bộ đọc chỉ đọc sheet đầu), sau đó là "Hướng dẫn" và "Danh mục" (cấp độ của giáo phận).
 */
public final class ImportTemplates {

    public static final String LEADERS_SHEET = "Huynh trưởng";
    public static final String DEANERIES_SHEET = "Giáo hạt";
    public static final String PARISHES_SHEET = "Giáo xứ";
    public static final String PARTICIPANTS_SHEET = "Danh sách tham dự";
    public static final String EXCEPTION_SHEET = "Ngoại lệ";
    private static final String GUIDE_SHEET = "Hướng dẫn";
    private static final String LOOKUP_SHEET = "Danh mục";
    private static final String LOOKUP_DEANERY_HEADER = "Giáo hạt (" + ParishImportService.COL_DEANERY + ")";
    private static final String LOOKUP_LEVEL_HEADER = "Cấp độ (" + LeaderImportService.COL_LEVEL + ")";
    private static final int VALIDATED_ROWS = 5000;

    private ImportTemplates() {
    }

    private record Column(String header, boolean required, int width, Kind kind) {
    }

    private enum Kind { TEXT, DATE, NUMBER, GENDER, RESULT, LEVEL, DEANERY, UNIT_TYPE, GENERAL }

    /** Mẫu import hồ sơ huynh trưởng. levelNames: tên cấp độ trong danh mục của giáo phận người tải (để chọn nhanh). */
    public static byte[] leaders(List<String> levelNames) {
        List<Column> columns = List.of(
                new Column(LeaderImportService.COL_HOLY_NAME, true, 16, Kind.GENERAL),
                new Column(LeaderImportService.COL_FULL_NAME, true, 28, Kind.GENERAL),
                new Column(LeaderImportService.COL_BIRTH_DATE, true, 14, Kind.DATE),
                new Column(LeaderImportService.COL_GENDER, true, 12, Kind.GENDER),
                new Column(LeaderImportService.COL_EMAIL, true, 32, Kind.GENERAL),
                new Column(LeaderImportService.COL_PHONE, true, 16, Kind.TEXT),
                new Column(LeaderImportService.COL_LEVEL, false, 26, Kind.LEVEL),
                new Column(LeaderImportService.COL_DEANERY, true, 22, Kind.GENERAL),
                new Column(LeaderImportService.COL_PARISH, true, 22, Kind.GENERAL),
                avatarColumn());
        List<List<Object>> samples = List.of(
                List.of("Phêrô", "Nguyễn Văn An", LocalDate.of(1995, 3, 7), "Nam", "nguyen.van.an@example.com", "0901234567", "", "Tên giáo hạt", "Tên giáo xứ", "nguyen-van-an.jpg"),
                List.of("Maria", "Trần Thị Bình", LocalDate.of(1998, 11, 21), "Nữ", "tran.thi.binh@example.com", "0912345678", "", "Tên giáo hạt", "Tên giáo xứ", ""));
        List<String> guide = List.of(
                "1. Điền dữ liệu ở sheet \"" + LEADERS_SHEET + "\" (sheet đầu tiên), từ dòng 2. Xóa các dòng ví dụ trước khi import. Tên cột có thể có hoặc không có dấu *, thứ tự cột không quan trọng.",
                "2. Tất cả các cột đều bắt buộc (tiêu đề màu đỏ), TRỪ cột Cấp độ và Tên file ảnh: Tên thánh, Họ và tên, Ngày sinh, Giới tính, Email, Số điện thoại, Giáo hạt, Giáo xứ.",
                "3. Ngày sinh theo dạng dd/MM/yyyy (ví dụ 07/03/1995), không được sau ngày hiện tại.",
                "4. Giới tính chỉ nhận: Nam hoặc Nữ (chọn từ danh sách).",
                "5. Email và Số điện thoại không được trùng với huynh trưởng đã có trong hệ thống hoặc trùng giữa các dòng trong file. Số điện thoại gồm 10 chữ số và bắt đầu bằng 0 (cột đã để định dạng Text để không mất số 0 đầu).",
                "6. Cấp độ: chọn từ danh sách (sheet \"" + LOOKUP_SHEET + "\") hoặc nhập tên / mã cấp độ trong danh mục của giáo phận. Để trống nghĩa là \"Chưa có cấp\".",
                "7. Giáo hạt và Giáo xứ (cột Giáo xứ cũng nhận tên giáo họ / giáo điểm) phải ĐÃ CÓ trong hệ thống (nhập đúng tên) và giáo xứ phải thuộc đúng giáo hạt đã nhập; nếu chưa có, dòng đó báo lỗi (hệ thống không tự tạo giáo hạt / giáo xứ).",
                "8. Chỉ nhập được huynh trưởng cho giáo xứ trong phạm vi tài khoản của bạn. Chỉ cần một dòng lỗi thì toàn bộ file không được lưu; hệ thống báo rõ sheet, dòng, cột bị lỗi ở khay tác vụ để bạn sửa rồi import lại.",
                "9. Mỗi lần import tối đa 5.000 dòng.",
                avatarGuide(10, false));
        return build(LEADERS_SHEET, "Hướng dẫn import hồ sơ huynh trưởng", columns, samples, guide, levelNames);
    }

    /** Mẫu import danh sách giáo hạt: chỉ bắt buộc Tên giáo hạt. */
    public static byte[] deaneries() {
        List<Column> columns = List.of(
                new Column(DeaneryImportService.COL_NAME, true, 28, Kind.GENERAL),
                new Column(DeaneryImportService.COL_PATRON_SAINT_DAY, false, 16, Kind.DATE),
                new Column(DeaneryImportService.COL_ESTABLISHMENT_DATE, false, 16, Kind.DATE),
                new Column(DeaneryImportService.COL_CHAPLAIN, false, 26, Kind.GENERAL),
                new Column(DeaneryImportService.COL_PHONE, false, 16, Kind.TEXT),
                new Column(DeaneryImportService.COL_EMAIL, false, 30, Kind.GENERAL),
                new Column(DeaneryImportService.COL_ASSOCIATION, false, 26, Kind.GENERAL));
        List<List<Object>> samples = List.of(
                List.of("Thánh Tâm", LocalDate.of(2000, 6, 16), LocalDate.of(1990, 1, 1), "Linh mục Phêrô Nguyễn Văn An", "0901234567", "giaohat.thanhtam@example.com", "Thánh Tâm"),
                List.of("Phú Cường"));
        List<String> guide = List.of(
                "1. Điền dữ liệu ở sheet \"" + DEANERIES_SHEET + "\" (sheet đầu tiên), từ dòng 2. Xóa các dòng ví dụ trước khi import. Tên cột có thể có hoặc không có dấu *, thứ tự cột không quan trọng.",
                "2. CHỈ cột Tên giáo hạt là bắt buộc (tiêu đề màu đỏ). Các cột còn lại có thể để trống.",
                "3. Giáo hạt được tạo trong giáo phận hiện tại của tài khoản. Tên giáo hạt tối đa 100 ký tự, không được trùng với giáo hạt đã có trong giáo phận hoặc trùng giữa các dòng trong file.",
                "4. Ngày bổn mạng, Ngày thành lập theo dạng dd/MM/yyyy (Ngày thành lập không được sau ngày hiện tại).",
                "5. Số điện thoại gồm 10 chữ số và bắt đầu bằng 0 (cột đã để định dạng Text để không mất số 0 đầu). Email phải đúng định dạng.",
                "6. Chỉ cần một dòng lỗi thì toàn bộ file không được lưu; hệ thống báo rõ sheet, dòng, cột bị lỗi ở khay tác vụ để bạn sửa rồi import lại. Mỗi lần import tối đa 5.000 dòng.");
        return build(DEANERIES_SHEET, "Hướng dẫn import danh sách giáo hạt", columns, samples, guide, List.of(), null, null, null, LOOKUP_DEANERY_HEADER);
    }

    /**
     * Mẫu import danh sách giáo xứ. deaneryNames: các giáo hạt có trong hệ thống mà người tải được phép tạo giáo xứ (danh sách chọn).
     * Bắt buộc: Tên giáo xứ, Tên giáo hạt; Loại để trống là giáo xứ, giáo họ / giáo điểm phải chọn Loại và nhập Giáo xứ cha.
     */
    public static byte[] parishes(List<String> deaneryNames) {
        String firstDeanery = deaneryNames.isEmpty() ? "Tên giáo hạt" : deaneryNames.get(0);
        List<Column> columns = List.of(
                new Column(ParishImportService.COL_NAME, true, 28, Kind.GENERAL),
                new Column(ParishImportService.COL_TYPE, false, 14, Kind.UNIT_TYPE),
                new Column(ParishImportService.COL_DEANERY, true, 26, Kind.DEANERY),
                new Column(ParishImportService.COL_PARENT, false, 26, Kind.GENERAL),
                new Column(ParishImportService.COL_PATRON_SAINT_DAY, false, 16, Kind.DATE),
                new Column(ParishImportService.COL_ESTABLISHMENT_DATE, false, 16, Kind.DATE),
                new Column(ParishImportService.COL_CHAPLAIN, false, 26, Kind.GENERAL),
                new Column(ParishImportService.COL_PHONE, false, 16, Kind.TEXT),
                new Column(ParishImportService.COL_EMAIL, false, 30, Kind.GENERAL),
                new Column(ParishImportService.COL_CHAPTER, false, 24, Kind.GENERAL));
        List<List<Object>> samples = List.of(
                List.of("Chính Tòa", "", firstDeanery, "", LocalDate.of(2000, 6, 16), LocalDate.of(1990, 1, 1), "Linh mục Phêrô Nguyễn Văn An", "0901234567", "giaoxu.chinhtoa@example.com", "Chính Tòa"),
                List.of("Bình An", "Giáo họ", firstDeanery, "Chính Tòa"),
                List.of("Hòa Bình", "Giáo điểm", firstDeanery, "Chính Tòa"));
        List<String> guide = List.of(
                "1. Điền dữ liệu ở sheet \"" + PARISHES_SHEET + "\" (sheet đầu tiên), từ dòng 2. Xóa các dòng ví dụ trước khi import. Tên cột có thể có hoặc không có dấu *, thứ tự cột không quan trọng.",
                "2. Bắt buộc (tiêu đề màu đỏ): Tên giáo xứ, Tên giáo hạt. Các cột còn lại có thể để trống, trừ khi Loại là Giáo họ / Giáo điểm (xem mục 4).",
                "3. Tên giáo hạt: chọn từ danh sách (sheet \"" + LOOKUP_SHEET + "\" là các giáo hạt đã có trong hệ thống mà bạn được phép tạo giáo xứ) hoặc nhập đúng tên. Giáo hạt chưa có trong hệ thống sẽ báo lỗi (hệ thống không tự tạo giáo hạt).",
                "4. Loại: để TRỐNG nếu là giáo xứ. Nếu là giáo họ hoặc giáo điểm thì BẮT BUỘC chọn \"Giáo họ\" / \"Giáo điểm\" (chọn từ danh sách) và nhập thêm cột Giáo xứ cha.",
                "5. Giáo xứ cha: tên một giáo xứ (không phải giáo họ / giáo điểm) CÙNG giáo hạt, đã có trong hệ thống hoặc là giáo xứ mới ở một dòng khác trong cùng file. Giáo xứ thì không được nhập cột này.",
                "6. Tên giáo xứ tối đa 100 ký tự, không được trùng với giáo xứ / giáo họ / giáo điểm đã có trong cùng giáo hạt hoặc trùng giữa các dòng trong file (cùng giáo hạt).",
                "7. Ngày bổn mạng, Ngày thành lập theo dạng dd/MM/yyyy (Ngày thành lập không được sau ngày hiện tại). Số điện thoại gồm 10 chữ số và bắt đầu bằng 0. Email phải đúng định dạng.",
                "8. Chỉ nhập được giáo xứ cho giáo hạt trong phạm vi tài khoản của bạn. Chỉ cần một dòng lỗi thì toàn bộ file không được lưu; hệ thống báo rõ sheet, dòng, cột bị lỗi ở khay tác vụ để bạn sửa rồi import lại. Mỗi lần import tối đa 5.000 dòng.");
        return build(PARISHES_SHEET, "Hướng dẫn import danh sách giáo xứ", columns, samples, guide, deaneryNames, null, null, null, LOOKUP_DEANERY_HEADER);
    }

    /** Mẫu import danh sách tham dự khóa quá khứ (mọi cột bắt buộc trừ Cấp độ và Nhận xét). */
    public static byte[] participants(List<String> levelNames) {
        List<Column> columns = List.of(
                new Column(PastCourseParticipantImportService.COL_EMAIL, true, 32, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_PHONE, true, 16, Kind.TEXT),
                new Column(PastCourseParticipantImportService.COL_SCORE, true, 12, Kind.NUMBER),
                new Column(PastCourseParticipantImportService.COL_RESULT, true, 14, Kind.RESULT),
                new Column(PastCourseParticipantImportService.COL_REVIEW, false, 30, Kind.GENERAL),
                new Column(LeaderImportService.COL_HOLY_NAME, true, 14, Kind.GENERAL),
                new Column(LeaderImportService.COL_FULL_NAME, true, 26, Kind.GENERAL),
                new Column(LeaderImportService.COL_BIRTH_DATE, true, 14, Kind.DATE),
                new Column(LeaderImportService.COL_GENDER, true, 12, Kind.GENDER),
                new Column(LeaderImportService.COL_LEVEL, false, 24, Kind.LEVEL),
                new Column(LeaderImportService.COL_DEANERY, true, 20, Kind.GENERAL),
                new Column(LeaderImportService.COL_PARISH, true, 20, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_NOTE, false, 30, Kind.GENERAL),
                avatarColumn());
        List<List<Object>> samples = List.of(
                List.of("nguyen.van.an@example.com", "0901234567", 8.5, "Đạt", "Hoàn thành tốt khóa huấn luyện", "Phêrô", "Nguyễn Văn An", LocalDate.of(1995, 3, 7), "Nam", "", "Tên giáo hạt", "Tên giáo xứ", "Ghi chú tùy chọn", "nguyen-van-an.jpg"),
                List.of("tran.thi.binh@example.com", "0912345678", 5, "Không đạt", "", "Maria", "Trần Thị Bình", LocalDate.of(1998, 11, 21), "Nữ", "", "Tên giáo hạt", "Tên giáo xứ", "", ""));
        List<String> guide = List.of(
                "1. Chỉ dùng cho khóa huấn luyện quá khứ (tạo bằng nút \"Tạo khóa quá khứ\"). Mở chi tiết khóa, vào tab \"Danh sách tham dự\" và bấm \"Import Excel\".",
                "2. Điền dữ liệu ở sheet \"" + PARTICIPANTS_SHEET + "\" (sheet đầu tiên), từ dòng 2. Xóa các dòng ví dụ trước khi import.",
                "3. MỌI DÒNG đều phải nhập đủ tất cả các cột (tiêu đề màu đỏ), chỉ được để trống cột Nhận xét, Cấp độ, Ghi chú và Tên file ảnh: Email, Số điện thoại, Tổng điểm, Kết quả, Tên thánh, Họ và tên, Ngày sinh, Giới tính, Giáo hạt, Giáo xứ.",
                "4. Huynh trưởng được tìm theo Email và Số điện thoại (hai thông tin này là duy nhất, nếu khớp hai huynh trưởng khác nhau thì báo lỗi). Không dùng mã định danh (UUID).",
                "5. Huynh trưởng ĐÃ CÓ HỒ SƠ: hệ thống chỉ thêm huynh trưởng đó vào khóa, KHÔNG tạo mới và KHÔNG sửa hồ sơ (thông tin trong dòng chỉ để kiểm tra đủ dữ liệu). Huynh trưởng CHƯA CÓ HỒ SƠ: hệ thống tạo hồ sơ từ chính dòng này rồi thêm vào khóa.",
                "6. Ngày sinh theo dạng dd/MM/yyyy; Giới tính chọn Nam hoặc Nữ; Số điện thoại gồm 10 chữ số và bắt đầu bằng 0; Cấp độ chọn từ sheet \"" + LOOKUP_SHEET + "\" (để trống là Chưa có cấp).",
                "7. Giáo hạt và Giáo xứ (cột Giáo xứ cũng nhận tên giáo họ / giáo điểm) phải ĐÃ CÓ trong hệ thống, giáo xứ phải thuộc đúng giáo hạt đã nhập và thuộc giáo phận (khóa giáo phận) hoặc giáo hạt (khóa giáo hạt) của khóa; giáo xứ phải đang hoạt động. Huynh trưởng đã có hồ sơ cũng phải thuộc giáo phận / giáo hạt của khóa.",
                "8. Kết quả: Đạt hoặc Không đạt (chọn từ danh sách). Tổng điểm từ 0 đến 1000.",
                "9. Một huynh trưởng chỉ xuất hiện một lần trong file và một lần trong khóa; huynh trưởng đã có trong danh sách tham dự của khóa sẽ báo lỗi.",
                "10. Nếu khóa có \"Cấp độ đạt được\", học viên Đạt sẽ tự chuyển sang tab \"Duyệt chứng nhận\". Khi duyệt phải chọn ngày duyệt; sau khi duyệt, cấp độ được ghi vào lịch sử cấp độ (cấp hiện tại chỉ tăng nếu cấp mới cao hơn).",
                "11. Chỉ cần một dòng lỗi thì toàn bộ file không được lưu (kể cả huynh trưởng mới); lỗi được báo rõ sheet, dòng, cột ở khay tác vụ để sửa rồi import lại. Mỗi lần import tối đa 5.000 dòng.",
                "12. Cột \"Ghi chú\" là TÙY CHỌN (có thể bỏ trống, tối đa 1.000 ký tự): ghi chú của hồ sơ đăng ký, hiển thị ở danh sách tham dự và sửa được sau khi import.",
                avatarGuide(13, false));
        return build(PARTICIPANTS_SHEET, "Hướng dẫn import danh sách tham dự khóa huấn luyện quá khứ", columns, samples, guide, levelNames);
    }

    /** Mẫu import danh sách tham dự khóa huấn luyện thường (không có điểm / kết quả; điểm được chấm ở tab Chấm điểm). */
    public static byte[] regularParticipants(List<String> levelNames) {
        List<Column> columns = List.of(
                new Column(PastCourseParticipantImportService.COL_EMAIL, true, 32, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_PHONE, true, 16, Kind.TEXT),
                new Column(LeaderImportService.COL_HOLY_NAME, true, 14, Kind.GENERAL),
                new Column(LeaderImportService.COL_FULL_NAME, true, 26, Kind.GENERAL),
                new Column(LeaderImportService.COL_BIRTH_DATE, true, 14, Kind.DATE),
                new Column(LeaderImportService.COL_GENDER, true, 12, Kind.GENDER),
                new Column(LeaderImportService.COL_LEVEL, false, 24, Kind.LEVEL),
                new Column(LeaderImportService.COL_DEANERY, true, 20, Kind.GENERAL),
                new Column(LeaderImportService.COL_PARISH, true, 20, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_NOTE, false, 30, Kind.GENERAL),
                avatarColumn());
        List<Column> exceptionColumns = new java.util.ArrayList<>(columns);
        exceptionColumns.add(new Column(PastCourseParticipantImportService.COL_EXCEPTION_REASON, true, 40, Kind.GENERAL));
        List<List<Object>> exceptionSamples = List.of(
                List.of("le.van.cuong@example.com", "0923456789", "Giuse", "Lê Văn Cường", LocalDate.of(2000, 5, 12), "Nam", "", "Tên giáo hạt", "Tên giáo xứ", "", "", "Chưa đủ số năm hoạt động nhưng được giáo xứ đề cử"));
        List<List<Object>> samples = List.of(
                List.of("nguyen.van.an@example.com", "0901234567", "Phêrô", "Nguyễn Văn An", LocalDate.of(1995, 3, 7), "Nam", "", "Tên giáo hạt", "Tên giáo xứ", "Ghi chú tùy chọn", "nguyen-van-an.jpg"),
                List.of("tran.thi.binh@example.com", "0912345678", "Maria", "Trần Thị Bình", LocalDate.of(1998, 11, 21), "Nữ", "", "Tên giáo hạt", "Tên giáo xứ", "", ""));
        List<String> guide = List.of(
                "1. Dùng cho khóa huấn luyện thường (không phải khóa quá khứ). Mở chi tiết khóa, vào tab \"Danh sách tham dự\" và bấm \"Nhập excel danh sách tham dự\".",
                "2. Có hai sheet dữ liệu, điền từ dòng 2 và xóa các dòng ví dụ trước khi import: sheet \"" + PARTICIPANTS_SHEET + "\" (sheet đầu tiên) cho huynh trưởng ĐỦ điều kiện của khóa; sheet \"" + EXCEPTION_SHEET + "\" cho huynh trưởng KHÔNG đủ điều kiện của khóa (điều kiện hiển thị ở phần \"Điều kiện của khóa\" trong chi tiết khóa). Có thể bỏ trống một trong hai sheet.",
                "3. MỌI DÒNG đều phải nhập đủ các cột (tiêu đề màu đỏ), chỉ được để trống cột Cấp độ: Email, Số điện thoại, Tên thánh, Họ và tên, Ngày sinh, Giới tính, Giáo hạt, Giáo xứ. Sheet \"" + EXCEPTION_SHEET + "\" có thêm cột bắt buộc \"Lí do ngoại lệ\".",
                "4. Huynh trưởng được tìm theo Email và Số điện thoại (hai thông tin này là duy nhất, nếu khớp hai huynh trưởng khác nhau thì báo lỗi). Không dùng mã định danh (UUID).",
                "5. Huynh trưởng ĐÃ CÓ HỒ SƠ: hệ thống chỉ thêm huynh trưởng đó vào khóa, KHÔNG tạo mới và KHÔNG sửa hồ sơ. Huynh trưởng CHƯA CÓ HỒ SƠ (ở cả hai sheet): hệ thống tạo hồ sơ từ chính dòng này rồi thêm vào khóa.",
                "6. Ngày sinh theo dạng dd/MM/yyyy; Giới tính chọn Nam hoặc Nữ; Số điện thoại gồm 10 chữ số và bắt đầu bằng 0; Cấp độ chọn từ sheet \"" + LOOKUP_SHEET + "\" (để trống là Chưa có cấp).",
                "7. Giáo hạt và Giáo xứ (cột Giáo xứ cũng nhận tên giáo họ / giáo điểm) phải ĐÃ CÓ trong hệ thống, giáo xứ phải thuộc đúng giáo hạt đã nhập và thuộc giáo phận (khóa giáo phận) hoặc giáo hạt (khóa giáo hạt) của khóa; giáo xứ phải đang hoạt động.",
                "8. Sheet \"" + PARTICIPANTS_SHEET + "\": huynh trưởng được đưa thẳng vào danh sách tham dự (đã duyệt), không qua đăng ký và duyệt đăng ký; nếu huynh trưởng không đủ điều kiện của khóa thì báo lỗi, hãy chuyển sang sheet \"" + EXCEPTION_SHEET + "\".",
                "9. Sheet \"" + EXCEPTION_SHEET + "\": huynh trưởng được đưa vào danh sách CHỜ DUYỆT đăng ký và gắn cờ ngoại lệ kèm lí do; người duyệt xử lý ở tab \"Duyệt đăng ký\". Huynh trưởng đã đủ điều kiện hoặc có cấp độ cao hơn cấp độ đạt được gần nhất của loại khóa thì báo lỗi.",
                "10. Một huynh trưởng chỉ xuất hiện một lần trong cả file; huynh trưởng đã có hồ sơ đăng ký / tham dự của khóa sẽ báo lỗi. Điểm được chấm sau ở tab \"Chấm điểm\".",
                "11. Chỉ cần một dòng lỗi thì toàn bộ file không được lưu (kể cả huynh trưởng mới); lỗi được báo rõ sheet, dòng, cột ở khay tác vụ để sửa rồi import lại. Mỗi lần import tối đa 5.000 dòng mỗi sheet.",
                "12. Cột \"Ghi chú\" là TÙY CHỌN (có thể bỏ trống, tối đa 1.000 ký tự): ghi chú của hồ sơ đăng ký, hiển thị ở danh sách tham dự và sửa được sau khi import.",
                avatarGuide(13, true));
        return build(PARTICIPANTS_SHEET, "Hướng dẫn import danh sách tham dự khóa huấn luyện", columns, samples, guide, levelNames,
                EXCEPTION_SHEET, exceptionColumns, exceptionSamples);
    }

    /** Mẫu import danh sách chia đội: như danh sách tham dự khóa thường, thêm cột Tên đội, không có sheet Ngoại lệ. */
    public static byte[] teamParticipants(List<String> levelNames) {
        List<Column> columns = List.of(
                new Column(PastCourseParticipantImportService.COL_EMAIL, true, 32, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_PHONE, true, 16, Kind.TEXT),
                new Column(LeaderImportService.COL_HOLY_NAME, true, 14, Kind.GENERAL),
                new Column(LeaderImportService.COL_FULL_NAME, true, 26, Kind.GENERAL),
                new Column(LeaderImportService.COL_BIRTH_DATE, true, 14, Kind.DATE),
                new Column(LeaderImportService.COL_GENDER, true, 12, Kind.GENDER),
                new Column(LeaderImportService.COL_LEVEL, false, 24, Kind.LEVEL),
                new Column(LeaderImportService.COL_DEANERY, true, 20, Kind.GENERAL),
                new Column(LeaderImportService.COL_PARISH, true, 20, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_TEAM, true, 20, Kind.GENERAL),
                new Column(PastCourseParticipantImportService.COL_NOTE, false, 30, Kind.GENERAL),
                avatarColumn());
        List<List<Object>> samples = List.of(
                List.of("nguyen.van.an@example.com", "0901234567", "Phêrô", "Nguyễn Văn An", LocalDate.of(1995, 3, 7), "Nam", "", "Tên giáo hạt", "Tên giáo xứ", "Đội 1", "Ghi chú tùy chọn", "nguyen-van-an.jpg"),
                List.of("tran.thi.binh@example.com", "0912345678", "Maria", "Trần Thị Bình", LocalDate.of(1998, 11, 21), "Nữ", "", "Tên giáo hạt", "Tên giáo xứ", "Đội 1", "", ""));
        List<String> guide = List.of(
                "1. Dùng cho khóa huấn luyện thường (không phải khóa quá khứ). Mở chi tiết khóa, vào tab \"Chia đội\" và bấm \"Nhập excel chia đội\".",
                "2. Chỉ có một sheet dữ liệu \"" + PARTICIPANTS_SHEET + "\" (sheet đầu tiên), không có sheet Ngoại lệ; điền từ dòng 2 và xóa các dòng ví dụ trước khi import.",
                "3. MỌI DÒNG đều phải nhập đủ các cột (tiêu đề màu đỏ), chỉ được để trống cột Cấp độ: Email, Số điện thoại, Tên thánh, Họ và tên, Ngày sinh, Giới tính, Giáo hạt, Giáo xứ, Tên đội (tối đa 150 ký tự).",
                "4. Huynh trưởng được tìm theo Email và Số điện thoại (hai thông tin này là duy nhất, nếu khớp hai huynh trưởng khác nhau thì báo lỗi). Huynh trưởng ĐÃ CÓ HỒ SƠ chỉ được thêm vào khóa (hồ sơ không bị sửa); CHƯA CÓ HỒ SƠ thì được tạo mới từ chính dòng này.",
                "5. Hồ sơ được import mặc định là ĐÃ DUYỆT đăng ký: vào thẳng danh sách tham dự của khóa và gắn vào đội theo cột Tên đội. Hệ thống không kiểm tra điều kiện tham dự của khóa ở bước này.",
                "6. Ngày sinh theo dạng dd/MM/yyyy; Giới tính chọn Nam hoặc Nữ; Số điện thoại gồm 10 chữ số và bắt đầu bằng 0; Cấp độ chọn từ sheet \"" + LOOKUP_SHEET + "\" (để trống là Chưa có cấp).",
                "7. Giáo hạt và Giáo xứ (cột Giáo xứ cũng nhận tên giáo họ / giáo điểm) phải ĐÃ CÓ trong hệ thống, giáo xứ phải thuộc đúng giáo hạt đã nhập và thuộc giáo phận (khóa giáo phận) hoặc giáo hạt (khóa giáo hạt) của khóa; giáo xứ phải đang hoạt động.",
                "8. Một huynh trưởng chỉ xuất hiện một lần trong cả file; huynh trưởng đã có hồ sơ đăng ký / tham dự của khóa sẽ báo lỗi.",
                "9. Chỉ cần một dòng lỗi thì toàn bộ file không được lưu (kể cả huynh trưởng mới); lỗi được báo rõ sheet, dòng, cột ở khay tác vụ để sửa rồi import lại. Mỗi lần import tối đa 5.000 dòng.",
                "10. Cột \"Ghi chú\" là TÙY CHỌN (có thể bỏ trống, tối đa 1.000 ký tự): ghi chú của hồ sơ đăng ký, hiển thị ở danh sách tham dự và sửa được sau khi import.",
                avatarGuide(11, false));
        return build(PARTICIPANTS_SHEET, "Hướng dẫn import danh sách chia đội khóa huấn luyện", columns, samples, guide, levelNames);
    }

    /** Cột "Tên file ảnh" (tùy chọn) dùng chung cho mọi mẫu có ảnh đại diện kèm file ZIP. */
    private static Column avatarColumn() {
        return new Column(LeaderImportService.COL_AVATAR_FILE, false, 28, Kind.GENERAL);
    }

    /** Dòng hướng dẫn ảnh đại diện (chung cho mọi mẫu); bothSheets: mẫu có hai sheet dữ liệu và cột ảnh dùng được ở cả hai. */
    private static String avatarGuide(int number, boolean bothSheets) {
        return number + ". Ảnh là TÙY CHỌN" + (bothSheets ? " (dùng được ở cả hai sheet)" : "")
                + ": đặt ảnh JPG/PNG/WEBP (tối đa 5MB/ảnh) vào một file ZIP, chọn file ZIP khi import và ghi đúng tên ở cột \"Tên file ảnh\". "
                + "Ảnh dọc tỉ lệ 2:3, tối thiểu khoảng 480x720, một người ở giữa, nhìn thẳng, rõ mặt và đủ sáng; hồ sơ đã có ảnh sẽ giữ ảnh cũ.";
    }

    private static byte[] build(String dataSheetName, String guideTitle, List<Column> columns, List<List<Object>> samples,
                                List<String> guide, List<String> levelNames) {
        return build(dataSheetName, guideTitle, columns, samples, guide, levelNames, null, null, null, LOOKUP_LEVEL_HEADER);
    }

    /** exceptionSheetName != null: thêm sheet thứ hai (hồ sơ ngoại lệ) ngay sau sheet dữ liệu, cùng cách tô tiêu đề và danh sách chọn. */
    private static byte[] build(String dataSheetName, String guideTitle, List<Column> columns, List<List<Object>> samples,
                                List<String> guide, List<String> levelNames,
                                String exceptionSheetName, List<Column> exceptionColumns, List<List<Object>> exceptionSamples) {
        return build(dataSheetName, guideTitle, columns, samples, guide, levelNames, exceptionSheetName, exceptionColumns, exceptionSamples, LOOKUP_LEVEL_HEADER);
    }

    /** lookupValues: giá trị ở sheet "Danh mục" (cấp độ hoặc giáo hạt, tùy mẫu) dùng cho danh sách chọn; lookupHeader: tiêu đề cột đó. */
    private static byte[] build(String dataSheetName, String guideTitle, List<Column> columns, List<List<Object>> samples,
                                List<String> guide, List<String> lookupValues,
                                String exceptionSheetName, List<Column> exceptionColumns, List<List<Object>> exceptionSamples,
                                String lookupHeader) {
        List<String> levelNames = lookupValues;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fillDataSheet(workbook, workbook.createSheet(dataSheetName), columns, samples, levelNames);
            if (exceptionSheetName != null) {
                fillDataSheet(workbook, workbook.createSheet(exceptionSheetName), exceptionColumns, exceptionSamples, levelNames);
            }

            // Danh mục cấp độ của giáo phận người tải: chọn nhanh bằng danh sách thả xuống.
            Sheet lookup = workbook.createSheet(LOOKUP_SHEET);
            lookup.setColumnWidth(0, 32 * 256);
            lookup.createRow(0).createCell(0).setCellValue(lookupHeader);
            for (int i = 0; i < levelNames.size(); i++) {
                lookup.createRow(i + 1).createCell(0).setCellValue(levelNames.get(i));
            }

            Sheet guideSheet = workbook.createSheet(GUIDE_SHEET);
            guideSheet.setColumnWidth(0, 120 * 256);
            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
            Font title = workbook.createFont();
            title.setBold(true);
            title.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(title);
            Cell titleCell = guideSheet.createRow(0).createCell(0);
            titleCell.setCellValue(guideTitle);
            titleCell.setCellStyle(titleStyle);
            for (int i = 0; i < guide.size(); i++) {
                Cell cell = guideSheet.createRow(i + 2).createCell(0);
                cell.setCellValue(guide.get(i));
                cell.setCellStyle(wrap);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không thể tạo file Excel mẫu", exception);
        }
    }

    private static void fillDataSheet(XSSFWorkbook workbook, Sheet sheet, List<Column> columns, List<List<Object>> samples, List<String> levelNames) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        Font requiredFont = workbook.createFont();
        requiredFont.setBold(true);
        requiredFont.setColor(IndexedColors.RED.getIndex());
        CellStyle requiredStyle = headerStyle(workbook, requiredFont, IndexedColors.GREY_25_PERCENT);
        CellStyle optionalStyle = headerStyle(workbook, bold, IndexedColors.LEMON_CHIFFON);
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

        Row header = sheet.createRow(0);
        for (int c = 0; c < columns.size(); c++) {
            Column column = columns.get(c);
            Cell cell = header.createCell(c);
            cell.setCellValue(column.required() ? column.header() + " *" : column.header());
            cell.setCellStyle(column.required() ? requiredStyle : optionalStyle);
            sheet.setColumnWidth(c, column.width() * 256);
            if (column.kind() == Kind.TEXT) {
                sheet.setDefaultColumnStyle(c, textStyle);
            } else if (column.kind() == Kind.DATE) {
                sheet.setDefaultColumnStyle(c, dateStyle);
            }
        }
        sheet.createFreezePane(0, 1);

        for (int r = 0; r < samples.size(); r++) {
            Row row = sheet.createRow(r + 1);
            List<Object> values = samples.get(r);
            for (int c = 0; c < values.size() && c < columns.size(); c++) {
                Object value = values.get(c);
                if (value == null || (value instanceof String text && text.isEmpty())) {
                    continue;
                }
                Cell cell = row.createCell(c);
                if (value instanceof LocalDate date) {
                    cell.setCellValue(date);
                    cell.setCellStyle(dateStyle);
                } else if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else {
                    cell.setCellValue(value.toString());
                    if (columns.get(c).kind() == Kind.TEXT) {
                        cell.setCellStyle(textStyle);
                    }
                }
            }
        }


        DataValidationHelper validations = sheet.getDataValidationHelper();
        for (int c = 0; c < columns.size(); c++) {
            DataValidationConstraint constraint = switch (columns.get(c).kind()) {
                case GENDER -> validations.createExplicitListConstraint(new String[]{"Nam", "Nữ"});
                case RESULT -> validations.createExplicitListConstraint(new String[]{"Đạt", "Không đạt"});
                case UNIT_TYPE -> validations.createExplicitListConstraint(new String[]{"Giáo họ", "Giáo điểm"});
                case LEVEL, DEANERY -> levelNames.isEmpty() ? null
                        : validations.createFormulaListConstraint("'" + LOOKUP_SHEET + "'!$A$2:$A$" + (levelNames.size() + 1));
                default -> null;
            };
            if (constraint != null) {
                DataValidation validation = validations.createValidation(constraint, new CellRangeAddressList(1, VALIDATED_ROWS, c, c));
                // Cho phép nhập tay (tên / mã cấp độ khác, "Dat"...): bộ đọc file chuẩn hóa dấu và hoa thường.
                validation.setShowErrorBox(false);
                // XSSF ghi cờ này ngược nghĩa với tên: true mới là HIỆN mũi tên danh sách chọn trong Excel (false làm mũi tên bị ẩn).
                validation.setSuppressDropDownArrow(true);
                sheet.addValidationData(validation);
            }
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook, Font font, IndexedColors fill) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(fill.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
