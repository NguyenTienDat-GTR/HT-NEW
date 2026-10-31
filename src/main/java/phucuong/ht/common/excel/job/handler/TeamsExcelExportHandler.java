package phucuong.ht.common.excel.job.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.excel.ExcelExportSupport;
import phucuong.ht.common.excel.TrainingExcelExports;
import phucuong.ht.common.excel.job.ExcelExportHandler;
import phucuong.ht.common.excel.job.ExcelJobKind;
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.text.Normalizer;
import java.util.List;

/** Xuất danh sách chia đội; tên file kèm tên khóa (không dấu) để biết file thuộc khóa nào. */
@Component
@RequiredArgsConstructor
public class TeamsExcelExportHandler implements ExcelExportHandler {

    private final CourseParticipationService courseParticipationService;
    private final TrainingCourseRepository trainingCourseRepository;

    @Override
    public ExcelJobKind kind() {
        return ExcelJobKind.EXPORT_TEAMS;
    }

    @Override
    public byte[] export(BaseSearchRequest request, Authentication authentication, JobProgress progress) {
        List<CourseParticipationResponse> rows = ExcelExportSupport.collectAll(
                request, page -> courseParticipationService.findParticipants(page, authentication), progress);
        return TrainingExcelExports.teams(rows.isEmpty() ? null : rows.get(0).getCourseName(), rows);
    }

    @Override
    public String fileBase(BaseSearchRequest request) {
        if (request == null || request.getFilters() == null) {
            return kind().fileBase();
        }
        Object code = request.getFilters().get("course.courseCode");
        if (code == null) {
            return kind().fileBase();
        }
        String slug = trainingCourseRepository.findByCodeWithType(String.valueOf(code))
                .map(course -> slug(course.getCourseName()))
                .orElse("");
        return slug.isEmpty() ? kind().fileBase() : kind().fileBase() + "-" + slug;
    }

    private static String slug(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return plain.length() > 80 ? plain.substring(0, 80).replaceAll("-+$", "") : plain;
    }
}
