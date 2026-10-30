package phucuong.ht.common.excel.job.handler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import phucuong.ht.common.excel.ExcelExportSupport;
import phucuong.ht.common.excel.OrganizationExcelExports;
import phucuong.ht.common.excel.TrainingExcelExports;
import phucuong.ht.common.excel.job.ExcelExportHandler;
import phucuong.ht.common.excel.job.ExcelJobKind;
import phucuong.ht.modules.certificate.service.CertificateService;
import phucuong.ht.modules.deanery.service.DeaneryService;
import phucuong.ht.modules.diocese.service.DioceseService;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.parish.service.ParishService;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseService;

/**
 * Khai báo từng loại xuất Excel. Mỗi handler gọi lại đúng hàm danh sách của module tương ứng
 * (nên giữ nguyên phạm vi quyền của người tạo job). Loại "chia đội" nằm riêng ở {@link TeamsExcelExportHandler}.
 */
@Configuration
public class ExcelExportHandlerConfig {

    @Bean
    ExcelExportHandler dioceseExport(DioceseService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_DIOCESES, (request, auth, progress) -> OrganizationExcelExports.dioceses(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress)));
    }

    @Bean
    ExcelExportHandler deaneryExport(DeaneryService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_DEANERIES, (request, auth, progress) -> OrganizationExcelExports.deaneries(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress)));
    }

    @Bean
    ExcelExportHandler parishExport(ParishService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_PARISHES, (request, auth, progress) -> OrganizationExcelExports.parishes(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress)));
    }

    @Bean
    ExcelExportHandler leaderExport(LeaderService service, LeaderLevelService leaderLevelService) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_LEADERS, (request, auth, progress) -> TrainingExcelExports.leaders(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress),
                leaderLevelService.levelNamesFor(auth)));
    }

    @Bean
    ExcelExportHandler courseExport(TrainingCourseService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_COURSES, (request, auth, progress) -> TrainingExcelExports.courses(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress)));
    }

    @Bean
    ExcelExportHandler participationExport(CourseParticipationService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_PARTICIPATIONS, (request, auth, progress) -> TrainingExcelExports.participations(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress)));
    }

    @Bean
    ExcelExportHandler participantExport(CourseParticipationService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_PARTICIPANTS, (request, auth, progress) -> TrainingExcelExports.participations(
                ExcelExportSupport.collectAll(request, page -> service.findParticipants(page, auth), progress)));
    }

    @Bean
    ExcelExportHandler certificateExport(CertificateService service) {
        return ExcelExportHandler.of(ExcelJobKind.EXPORT_CERTIFICATES, (request, auth, progress) -> TrainingExcelExports.certificates(
                ExcelExportSupport.collectAll(request, page -> service.findAll(page, auth), progress)));
    }
}
