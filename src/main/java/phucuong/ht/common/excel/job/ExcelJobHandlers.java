package phucuong.ht.common.excel.job;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.excel.ExcelExportSupport;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.OrganizationExcelExports;
import phucuong.ht.common.excel.TrainingExcelExports;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.certificate.service.CertificateService;
import phucuong.ht.modules.deanery.service.DeaneryService;
import phucuong.ht.modules.diocese.service.DioceseService;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.parish.service.ParishService;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseService;

import java.nio.file.Path;

/**
 * Nội dung từng loại tác vụ nền. Xuất gọi lại đúng hàm danh sách của từng module (nên giữ nguyên phạm vi quyền của người tạo job),
 * nhập gọi service import trong transaction do worker mở.
 */
@Component
@RequiredArgsConstructor
public class ExcelJobHandlers {

    private final DioceseService dioceseService;
    private final DeaneryService deaneryService;
    private final ParishService parishService;
    private final LeaderService leaderService;
    private final LeaderLevelService leaderLevelService;
    private final TrainingCourseService trainingCourseService;
    private final CourseParticipationService courseParticipationService;
    private final CertificateService certificateService;
    private final LeaderImportService leaderImportService;
    private final PastCourseParticipantImportService pastCourseParticipantImportService;
    private final phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository trainingCourseRepository;

    /** Phần đầu tên file xuất; danh sách chia đội kèm tên khóa (không dấu) để biết file thuộc khóa nào. */
    public String exportFileBase(ExcelJobKind kind, BaseSearchRequest request) {
        if (kind != ExcelJobKind.EXPORT_TEAMS || request == null || request.getFilters() == null) {
            return kind.fileBase();
        }
        Object code = request.getFilters().get("course.courseCode");
        if (code == null) {
            return kind.fileBase();
        }
        String slug = trainingCourseRepository.findByCodeWithType(String.valueOf(code))
                .map(course -> slug(course.getCourseName()))
                .orElse("");
        return slug.isEmpty() ? kind.fileBase() : kind.fileBase() + "-" + slug;
    }

    private static String slug(String text) {
        if (text == null) {
            return "";
        }
        String plain = java.text.Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return plain.length() > 80 ? plain.substring(0, 80).replaceAll("-+$", "") : plain;
    }

    public byte[] export(ExcelJobKind kind, BaseSearchRequest request, Authentication authentication, JobProgress progress) {
        return switch (kind) {
            case EXPORT_DIOCESES -> OrganizationExcelExports.dioceses(
                    ExcelExportSupport.collectAll(request, page -> dioceseService.findAll(page, authentication), progress));
            case EXPORT_DEANERIES -> OrganizationExcelExports.deaneries(
                    ExcelExportSupport.collectAll(request, page -> deaneryService.findAll(page, authentication), progress));
            case EXPORT_PARISHES -> OrganizationExcelExports.parishes(
                    ExcelExportSupport.collectAll(request, page -> parishService.findAll(page, authentication), progress));
            case EXPORT_LEADERS -> TrainingExcelExports.leaders(
                    ExcelExportSupport.collectAll(request, page -> leaderService.findAll(page, authentication), progress),
                    leaderLevelService.levelNamesFor(authentication));
            case EXPORT_COURSES -> TrainingExcelExports.courses(
                    ExcelExportSupport.collectAll(request, page -> trainingCourseService.findAll(page, authentication), progress));
            case EXPORT_PARTICIPATIONS -> TrainingExcelExports.participations(
                    ExcelExportSupport.collectAll(request, page -> courseParticipationService.findAll(page, authentication), progress));
            case EXPORT_PARTICIPANTS -> TrainingExcelExports.participations(
                    ExcelExportSupport.collectAll(request, page -> courseParticipationService.findParticipants(page, authentication), progress));
            case EXPORT_TEAMS -> {
                List<phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse> rows = ExcelExportSupport.collectAll(
                        request, page -> courseParticipationService.findParticipants(page, authentication), progress);
                yield TrainingExcelExports.teams(rows.isEmpty() ? null : rows.get(0).getCourseName(), rows);
            }
            case EXPORT_CERTIFICATES -> TrainingExcelExports.certificates(
                    ExcelExportSupport.collectAll(request, page -> certificateService.findAll(page, authentication), progress));
            default -> throw new IllegalArgumentException("Không phải tác vụ xuất: " + kind);
        };
    }

    public ImportSummary runImport(ExcelJobKind kind, String target, Path file, Authentication authentication, JobProgress progress,
                                   AvatarZip images) {
        return switch (kind) {
            case IMPORT_LEADERS -> leaderImportService.importLeaders(file, authentication, progress, images);
            case IMPORT_PARTICIPANTS -> pastCourseParticipantImportService.importParticipants(target, file, authentication, progress, images);
            case IMPORT_TEAM_PARTICIPANTS -> pastCourseParticipantImportService.importParticipants(target, file, authentication, progress, true);
            default -> throw new IllegalArgumentException("Không phải tác vụ nhập: " + kind);
        };
    }
}
