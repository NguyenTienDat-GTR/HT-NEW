package phucuong.ht.common.excel.job.handler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.excel.ExcelColumn;
import phucuong.ht.common.excel.ExcelColumns;
import phucuong.ht.common.excel.ExcelExportSupport;
import phucuong.ht.common.excel.ExcelExportWriter;
import phucuong.ht.common.excel.ExcelReportHeaderResolver;
import phucuong.ht.common.excel.OrganizationExcelExports;
import phucuong.ht.common.excel.TrainingExcelExports;
import phucuong.ht.common.excel.job.ExcelExportHandler;
import phucuong.ht.common.excel.job.ExcelJobKind;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.certificate.service.CertificateService;
import phucuong.ht.modules.deanery.service.DeaneryService;
import phucuong.ht.modules.diocese.service.DioceseService;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.parish.service.ParishService;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseService;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Khai báo từng loại xuất Excel. Mỗi handler gọi lại đúng hàm danh sách của module tương ứng
 * (nên giữ nguyên phạm vi quyền của người tạo job). File có phần đầu giáo phận / giáo hạt / giáo xứ + tiêu đề
 * ({@link ExcelReportHeaderResolver}) và chỉ gồm các cột người dùng chọn. Loại "chia đội" nằm riêng ở {@link TeamsExcelExportHandler}.
 */
@Configuration
public class ExcelExportHandlerConfig {

    @Bean
    ExcelExportHandler dioceseExport(DioceseService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_DIOCESES, "Giáo phận", "Danh sách giáo phận", false, headers,
                auth -> OrganizationExcelExports.dioceseColumns(), service::findAll);
    }

    @Bean
    ExcelExportHandler deaneryExport(DeaneryService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_DEANERIES, "Giáo hạt", "Danh sách giáo hạt", false, headers,
                auth -> OrganizationExcelExports.deaneryColumns(), service::findAll);
    }

    @Bean
    ExcelExportHandler parishExport(ParishService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_PARISHES, "Giáo xứ", "Danh sách giáo xứ", false, headers,
                auth -> OrganizationExcelExports.parishColumns(), service::findAll);
    }

    @Bean
    ExcelExportHandler leaderExport(LeaderService service, LeaderLevelService leaderLevelService, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_LEADERS, "Hồ sơ huynh trưởng", "Danh sách huynh trưởng", false, headers,
                auth -> auth == null ? TrainingExcelExports.leaderColumns(Map.of()) : TrainingExcelExports.leaderColumns(leaderLevelService.levelNamesFor(auth)),
                service::findAll);
    }

    @Bean
    ExcelExportHandler courseExport(TrainingCourseService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_COURSES, "Khóa huấn luyện", "Danh sách khóa huấn luyện", false, headers,
                auth -> TrainingExcelExports.courseColumns(), service::findAll);
    }

    @Bean
    ExcelExportHandler participationExport(CourseParticipationService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_PARTICIPATIONS, "Đăng ký tham dự", "Danh sách đăng ký tham dự", true, headers,
                auth -> TrainingExcelExports.participationColumns(), service::findAll, TrainingExcelExports::expandComponentScores);
    }

    @Bean
    ExcelExportHandler participantExport(CourseParticipationService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_PARTICIPANTS, "Danh sách tham dự", "Danh sách tham dự", true, headers,
                auth -> TrainingExcelExports.participationColumns(), service::findParticipants, TrainingExcelExports::expandComponentScores);
    }

    @Bean
    ExcelExportHandler certificateExport(CertificateService service, ExcelReportHeaderResolver headers) {
        return listing(ExcelJobKind.EXPORT_CERTIFICATES, "Chứng nhận", "Danh sách chứng nhận", true, headers,
                auth -> TrainingExcelExports.certificateColumns(), service::findAll);
    }

    /**
     * Handler xuất một danh sách: gom toàn bộ theo bộ lọc, dựng phần đầu, lọc cột theo lựa chọn rồi ghi .xlsx.
     * columns(null) dùng để liệt kê tên cột cho người dùng chọn (không cần tài khoản).
     */
    static <R> ExcelExportHandler listing(ExcelJobKind kind, String sheetName, String title, boolean withCourse,
                                          ExcelReportHeaderResolver headers,
                                          Function<Authentication, List<ExcelColumn<R>>> columns,
                                          BiFunction<BaseSearchRequest, Authentication, Page<R>> fetch) {
        return listing(kind, sheetName, title, withCourse, headers, columns, fetch, (selected, rows) -> selected);
    }

    /**
     * Như trên, nhưng cho phép biến đổi danh sách cột đã chọn sau khi đã có dữ liệu (vd. tách "Điểm thành phần"
     * thành mỗi thành phần một cột, mà số cột chỉ biết khi đã đọc xong các dòng).
     */
    static <R> ExcelExportHandler listing(ExcelJobKind kind, String sheetName, String title, boolean withCourse,
                                          ExcelReportHeaderResolver headers,
                                          Function<Authentication, List<ExcelColumn<R>>> columns,
                                          BiFunction<BaseSearchRequest, Authentication, Page<R>> fetch,
                                          BiFunction<List<ExcelColumn<R>>, List<R>, List<ExcelColumn<R>>> expand) {
        return ExcelExportHandler.of(kind, ExcelColumns.headers(columns.apply(null)), (request, auth, progress) -> {
            List<R> rows = ExcelExportSupport.collectAll(request.search(), page -> fetch.apply(page, auth), progress);
            List<ExcelColumn<R>> selected = expand.apply(ExcelColumns.select(columns.apply(auth), request.columns()), rows);
            return ExcelExportWriter.write(sheetName, headers.resolve(request.search(), auth, title, withCourse), selected, rows);
        });
    }
}
