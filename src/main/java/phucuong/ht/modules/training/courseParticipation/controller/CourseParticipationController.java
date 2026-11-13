package phucuong.ht.modules.training.courseParticipation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingBulkApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreNoteRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.RegistrationCandidateResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.ScoreEntryComponentResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingBulkApprovalResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingRegistrationBatchResponse;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.common.repository.BaseSearchRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class CourseParticipationController {

    private final CourseParticipationService courseParticipationService;
    private final phucuong.ht.common.excel.job.ExcelJobService excelJobService;
    private final phucuong.ht.modules.training.registrationTemplate.service.RegistrationTemplateService registrationTemplateService;

    @GetMapping("/participations")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public apiResponse<Page<CourseParticipationResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách hồ sơ đào tạo thành công", courseParticipationService.findAll(request, authentication));
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/participations/export")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> exportParticipations(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_PARTICIPATIONS, request, idempotencyKey, authentication));
    }

    // Xuất danh sách đăng ký của một khóa theo mẫu Excel của giáo phận (chạy nền). Kiểm tra trước khóa và mẫu để báo lỗi ngay.
    // Tùy chọn: groupBy = NONE | DEANERY | PARISH; layout = SINGLE_SHEET | SHEET_PER_GROUP | FILE_PER_GROUP.
    @org.springframework.web.bind.annotation.PostMapping("/participations/template-export")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> exportParticipationsByTemplate(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        Object courseCode = request.getFilters() == null ? null : request.getFilters().get("course.courseCode");
        registrationTemplateService.forCourse(courseCode == null ? null : String.valueOf(courseCode));
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_REGISTRATION_TEMPLATE, request, idempotencyKey, authentication));
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/participants/export")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> exportParticipants(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_PARTICIPANTS, request, idempotencyKey, authentication));
    }

    // Xuất Excel chia đội (tên thánh, họ tên, giáo xứ, giáo hạt, đội; tên khóa ở dòng tiêu đề) theo bộ lọc của tab Chia đội.
    @org.springframework.web.bind.annotation.PostMapping("/participants/teams/export")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> exportTeams(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_TEAMS, request, idempotencyKey, authentication));
    }

    @GetMapping("/courses/{courseCode}/score-components")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScoreCourse(authentication, #courseCode)")
    public apiResponse<List<ScoreEntryComponentResponse>> scoreComponents(@PathVariable String courseCode) {
        return apiResponse.success("Lấy thành phần điểm của khóa thành công", courseParticipationService.findScoreComponents(courseCode));
    }

    @GetMapping("/participants")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public apiResponse<Page<CourseParticipationResponse>> findParticipants(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách tham dự thành công", courseParticipationService.findParticipants(request, authentication));
    }

    @GetMapping("/participants/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadOne(authentication, #id)")
    public apiResponse<CourseParticipationResponse> findParticipantById(@PathVariable String id) {
        return apiResponse.success("Lấy chi tiết tham dự thành công", courseParticipationService.findParticipantById(id));
    }

    @DeleteMapping("/participants/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRemoveParticipant(authentication)")
    public apiResponse<Void> removeParticipant(@PathVariable String id, Authentication authentication) {
        courseParticipationService.removeParticipant(id, authentication);
        return apiResponse.success("Đã chuyển hồ sơ khỏi danh sách tham dự về trạng thái từ chối duyệt", null);
    }

    @GetMapping("/participations/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadOne(authentication, #id)")
    public apiResponse<CourseParticipationResponse> findById(@PathVariable String id) {
        return apiResponse.success("Lấy chi tiết hồ sơ đào tạo thành công", courseParticipationService.findById(id));
    }

    @GetMapping("/courses/{courseCode}/registration-candidates")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRegister(authentication, #courseCode)")
    public apiResponse<Page<RegistrationCandidateResponse>> registrationCandidates(
            @PathVariable String courseCode,
            @RequestParam(defaultValue = "ELIGIBLE") String mode,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID deaneryId,
            @RequestParam(required = false) UUID parishId,
            @RequestParam(required = false) String leaderLevel,
            @RequestParam(required = false) List<UUID> excludeLeaderIds,
            @RequestParam(required = false) phucuong.ht.common.enums.UnitType senderType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        boolean eligibleOnly = !"EXCEPTION".equalsIgnoreCase(mode);
        return apiResponse.success("Lấy danh sách huynh trưởng lập danh sách đăng ký thành công",
                courseParticipationService.findRegistrationCandidates(courseCode, eligibleOnly, search, deaneryId, parishId, leaderLevel, excludeLeaderIds, senderType, Math.max(page, 0), Math.min(Math.max(size, 1), BaseSearchRequest.MAX_PAGE_SIZE), authentication));
    }

    @PostMapping("/courses/{courseCode}/registrations")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRegister(authentication, #courseCode)")
    public apiResponse<TrainingRegistrationBatchResponse> register(
            @PathVariable String courseCode,
            @Valid @RequestBody TrainingRegistrationRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gửi danh sách đăng ký khóa huấn luyện thành công", courseParticipationService.register(courseCode, request, authentication));
    }

    @DeleteMapping("/registrations/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canWithdraw(authentication)")
    public apiResponse<Void> withdraw(@PathVariable String id, Authentication authentication) {
        courseParticipationService.withdraw(id, authentication);
        return apiResponse.success("Thu hồi đăng ký thành công", null);
    }

    @PostMapping("/registrations/bulk-approval")
    @PreAuthorize("@courseParticipationAccessEvaluator.canBulkApprove(authentication)")
    public apiResponse<TrainingBulkApprovalResponse> bulkApprove(@Valid @RequestBody TrainingBulkApprovalRequest request, Authentication authentication) {
        return apiResponse.success("Xử lý duyệt hàng loạt hoàn tất", courseParticipationService.bulkApprove(request, authentication));
    }

    @PostMapping("/registrations/{id}/deanery-approval")
    @PreAuthorize("@courseParticipationAccessEvaluator.canDeaneryApprove(authentication, #id)")
    public apiResponse<CourseParticipationResponse> deaneryApprove(
            @PathVariable String id,
            @Valid @RequestBody TrainingApprovalRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Duyệt đăng ký cấp giáo hạt thành công", courseParticipationService.deaneryApprove(id, request, authentication));
    }

    @PostMapping("/registrations/{id}/diocese-approval")
    @PreAuthorize("@courseParticipationAccessEvaluator.canDioceseApprove(authentication, #id)")
    public apiResponse<CourseParticipationResponse> dioceseApprove(
            @PathVariable String id,
            @Valid @RequestBody TrainingApprovalRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Duyệt đăng ký cấp giáo phận thành công", courseParticipationService.dioceseApprove(id, request, authentication));
    }

    // Ghi chú của người gửi đăng ký: người duyệt đúng đơn vị sở hữu khóa hoặc người gửi khi hồ sơ còn chờ duyệt (kiểm tra chi tiết ở service).
    @PatchMapping("/participations/{id}/registration-note")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public apiResponse<CourseParticipationResponse> updateRegistrationNote(
            @PathVariable String id,
            @Valid @RequestBody phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationNoteRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật ghi chú thành công", courseParticipationService.updateRegistrationNote(id, request, authentication));
    }

    @PatchMapping("/participations/{id}/score-note")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScore(authentication, #id)")
    public apiResponse<CourseParticipationResponse> updateScoreNote(
            @PathVariable String id,
            @Valid @RequestBody TrainingScoreNoteRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật nhận xét thành công", courseParticipationService.updateScoreNote(id, request, authentication));
    }

    // Tạo chứng nhận ngoại lệ (chờ duyệt) cho hồ sơ không đạt: người chấm điểm đúng cấp quản lý của khóa.
    @PostMapping("/participations/{id}/exception-certificate")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScore(authentication, #id)")
    public apiResponse<CourseParticipationResponse> createExceptionCertificate(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Đã tạo chứng nhận ngoại lệ chờ duyệt", courseParticipationService.createExceptionCertificate(id, authentication));
    }

    @PatchMapping("/participations/{id}/score")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScore(authentication, #id)")
    public apiResponse<CourseParticipationResponse> score(
            @PathVariable String id,
            @Valid @RequestBody TrainingScoreRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật điểm thành công", courseParticipationService.score(id, request, authentication));
    }
}
