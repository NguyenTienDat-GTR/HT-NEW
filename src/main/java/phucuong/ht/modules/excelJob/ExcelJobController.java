package phucuong.ht.modules.excelJob;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.excel.DuplicateDecision;
import phucuong.ht.common.excel.ExcelResponses;
import phucuong.ht.common.excel.job.ExcelJobKind;
import phucuong.ht.common.excel.job.ExcelJobResponses;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;
import phucuong.ht.common.excel.job.ExcelJobResponse;
import phucuong.ht.common.excel.job.ExcelJobService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Xem tiến độ, hủy và tải kết quả các tác vụ Excel nền của chính mình. Việc tạo tác vụ nằm ở endpoint của từng module (giữ nguyên quyền). */
@RestController
@RequestMapping("/api/excel-jobs")
@RequiredArgsConstructor
public class ExcelJobController {

    private final ExcelJobService excelJobService;
    private final LeaderAccessEvaluator leaderAccessEvaluator;
    private final TrainingCourseAccessEvaluator trainingCourseAccessEvaluator;
    private final PastCourseParticipantImportService pastCourseParticipantImportService;

    @GetMapping
    public apiResponse<List<ExcelJobResponse>> listMine(Authentication authentication) {
        return apiResponse.success("Lấy danh sách tác vụ thành công", excelJobService.listMine(authentication));
    }

    @GetMapping("/{id}")
    public apiResponse<ExcelJobResponse> get(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy trạng thái tác vụ thành công", excelJobService.get(id, authentication));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable UUID id, Authentication authentication) {
        ExcelJobService.Download download = excelJobService.download(id, authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(ExcelResponses.XLSX)
                .body(new FileSystemResource(download.path()));
    }

    @PostMapping("/{id}/cancel")
    public apiResponse<ExcelJobResponse> cancel(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Đã gửi yêu cầu hủy tác vụ", excelJobService.cancel(id, authentication));
    }

    public record ResolveDuplicatesRequest(@NotEmpty(message = "Chưa có quyết định nào") List<@Valid DuplicateDecision> decisions) {
    }

    // Xác nhận các hồ sơ nghi trùng của một lần import đã xong (cùng một người / hai người khác nhau) rồi xử lý tiếp các dòng đó bằng một tác vụ mới.
    // Quyền kiểm tra lại đúng như lúc tạo import (job chỉ của chính người tạo).
    @PostMapping("/{id}/resolve-duplicates")
    public ResponseEntity<apiResponse<ExcelJobResponse>> resolveDuplicates(@PathVariable UUID id, @Valid @RequestBody ResolveDuplicatesRequest request,
                                                                          Authentication authentication) {
        ExcelJobService.DuplicateScope scope = excelJobService.scopeOf(id, authentication);
        boolean allowed = switch (scope.kind()) {
            case IMPORT_LEADERS -> leaderAccessEvaluator.canCreate(authentication);
            case IMPORT_PARTICIPANTS -> trainingCourseAccessEvaluator.canImportParticipants(authentication, scope.target());
            case IMPORT_TEAM_PARTICIPANTS -> trainingCourseAccessEvaluator.canImportRegularParticipants(authentication);
            default -> false;
        };
        if (!allowed) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Không có quyền xác nhận hồ sơ trùng của tác vụ này");
        }
        if (scope.kind() != ExcelJobKind.IMPORT_LEADERS) {
            pastCourseParticipantImportService.precheck(scope.target(), authentication, scope.kind() == ExcelJobKind.IMPORT_TEAM_PARTICIPANTS);
        }
        return ExcelJobResponses.accepted(excelJobService.resolveDuplicates(id, request.decisions(), authentication));
    }
}
