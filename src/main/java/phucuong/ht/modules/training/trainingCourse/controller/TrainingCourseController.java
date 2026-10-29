package phucuong.ht.modules.training.trainingCourse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseStatusToggleRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.CourseStatusImpactResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseScoreLockRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.CoursePassingScoreLimitResponse;
import phucuong.ht.modules.training.trainingCourse.model.response.CourseRecipientCandidateResponse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseCreateRequest;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseService;

@RestController
@RequestMapping("/api/training/courses")
@RequiredArgsConstructor
public class TrainingCourseController {

    private final TrainingCourseService trainingCourseService;
    private final phucuong.ht.common.excel.job.ExcelJobService excelJobService;
    private final phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;
    private final phucuong.ht.modules.training.trainingCourse.service.PastTrainingCourseService pastTrainingCourseService;
    private final phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService pastCourseParticipantImportService;

    @PostMapping
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreate(authentication, #request.courseTypeCode)")
    public apiResponse<TrainingCourseResponse> create(@Valid @RequestBody TrainingCourseCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo khóa huấn luyện thành công", trainingCourseService.create(request, authentication));
    }

    // Khóa quá khứ: chỉ tài khoản được super-admin cấp quyền training.course.create_past.diocese.
    @PostMapping("/past")
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreatePast(authentication)")
    public apiResponse<TrainingCourseResponse> createPast(@Valid @RequestBody phucuong.ht.modules.training.trainingCourse.model.request.PastTrainingCourseRequest request,
                                                          Authentication authentication) {
        return apiResponse.success("Tạo khóa huấn luyện quá khứ thành công", pastTrainingCourseService.create(request, authentication));
    }

    // Tải file Excel mẫu danh sách tham dự khóa quá khứ (cùng quyền với tạo khóa quá khứ).
    @GetMapping("/past/import-template")
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreatePast(authentication)")
    public org.springframework.http.ResponseEntity<byte[]> participantsImportTemplate(Authentication authentication) {
        return phucuong.ht.common.excel.ExcelResponses.xlsx(
                phucuong.ht.common.excel.ImportTemplates.participants(leaderLevelService.selectableLevelNames(authentication)),
                "mau-import-danh-sach-tham-du-khoa-qua-khu");
    }

    // Tải file Excel mẫu danh sách tham dự khóa thường (không có điểm / kết quả): cùng quyền với duyệt đăng ký.
    @GetMapping("/participants/import-template")
    @PreAuthorize("@trainingCourseAccessEvaluator.canImportRegularParticipants(authentication)")
    public org.springframework.http.ResponseEntity<byte[]> regularParticipantsImportTemplate(Authentication authentication) {
        return phucuong.ht.common.excel.ExcelResponses.xlsx(
                phucuong.ht.common.excel.ImportTemplates.regularParticipants(leaderLevelService.selectableLevelNames(authentication)),
                "mau-import-danh-sach-tham-du");
    }

    // Import danh sách tham dự từ file Excel, chạy nền. Khóa quá khứ: email / SĐT, tổng điểm, kết quả; khóa thường: chỉ email / SĐT và thông tin huynh trưởng.
    // Bắt buộc có Idempotency-Key: gửi lại cùng key trả lại đúng tác vụ cũ, không import hai lần.
    @PostMapping(value = "/{courseCode}/participants/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@trainingCourseAccessEvaluator.canImportParticipants(authentication, #courseCode)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> importParticipants(
            @PathVariable String courseCode,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestPart(value = "images", required = false) org.springframework.web.multipart.MultipartFile images,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        // Báo lỗi ngay nếu khóa không thuộc đơn vị của mình thay vì đợi tác vụ chạy xong.
        pastCourseParticipantImportService.precheck(courseCode, authentication);
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(excelJobService.createImport(
                phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_PARTICIPANTS, courseCode, file, images, idempotencyKey, authentication));
    }

    // Tải file Excel mẫu danh sách chia đội (giống danh sách tham dự khóa thường, thêm cột Tên đội, không có sheet Ngoại lệ).
    @GetMapping("/teams/import-template")
    @PreAuthorize("@trainingCourseAccessEvaluator.canImportRegularParticipants(authentication)")
    public org.springframework.http.ResponseEntity<byte[]> teamParticipantsImportTemplate(Authentication authentication) {
        return phucuong.ht.common.excel.ExcelResponses.xlsx(
                phucuong.ht.common.excel.ImportTemplates.teamParticipants(leaderLevelService.selectableLevelNames(authentication)),
                "mau-import-danh-sach-chia-doi");
    }

    // Import danh sách chia đội từ file Excel, chạy nền; hồ sơ vào thẳng danh sách tham dự (đã duyệt) kèm tên đội. Chỉ khóa thường.
    @PostMapping(value = "/{courseCode}/teams/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@trainingCourseAccessEvaluator.canImportRegularParticipants(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> importTeamParticipants(
            @PathVariable String courseCode,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        pastCourseParticipantImportService.precheck(courseCode, authentication, true);
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(excelJobService.createImport(
                phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_TEAM_PARTICIPANTS, courseCode, file, idempotencyKey, authentication));
    }

    // Xóa học viên nhập nhầm khỏi khóa quá khứ.
    @DeleteMapping("/{courseCode}/participants/{participationId}")
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreatePast(authentication)")
    public apiResponse<Void> removePastParticipant(@PathVariable String courseCode, @PathVariable String participationId, Authentication authentication) {
        pastCourseParticipantImportService.removeParticipant(courseCode, participationId, authentication);
        return apiResponse.success("Đã xóa học viên khỏi khóa quá khứ", null);
    }

    @PutMapping("/past/{courseCode}")
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreatePast(authentication)")
    public apiResponse<TrainingCourseResponse> updatePast(@PathVariable String courseCode,
                                                          @Valid @RequestBody phucuong.ht.modules.training.trainingCourse.model.request.PastTrainingCourseRequest request,
                                                          Authentication authentication) {
        return apiResponse.success("Cập nhật khóa huấn luyện quá khứ thành công", pastTrainingCourseService.update(courseCode, request, authentication));
    }

    @GetMapping
    @PreAuthorize("@trainingCourseAccessEvaluator.canList(authentication)")
    public apiResponse<Page<TrainingCourseResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa huấn luyện thành công", trainingCourseService.findAll(request, authentication));
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/export")
    @PreAuthorize("@trainingCourseAccessEvaluator.canList(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> export(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_COURSES, request, idempotencyKey, authentication));
    }

    @PatchMapping("/{courseCode}/score-lock")
    @PreAuthorize("@trainingCourseAccessEvaluator.canLockScore(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> setScoreLock(
            @PathVariable String courseCode,
            @Valid @RequestBody CourseScoreLockRequest request,
            Authentication authentication
    ) {
        return apiResponse.success(
                Boolean.TRUE.equals(request.getLocked()) ? "Đã khóa chỉnh sửa điểm" : "Đã mở chỉnh sửa điểm",
                trainingCourseService.setScoreLocked(courseCode, Boolean.TRUE.equals(request.getLocked()), authentication));
    }

    @PostMapping("/{courseCode}/finish")
    @PreAuthorize("@trainingCourseAccessEvaluator.canFinish(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> finish(@PathVariable String courseCode, Authentication authentication) {
        return apiResponse.success("Đã kết thúc khóa huấn luyện", trainingCourseService.finishCourse(courseCode, authentication));
    }

    @GetMapping("/registrable")
    @PreAuthorize("@trainingCourseAccessEvaluator.canListRegistrable(authentication)")
    public apiResponse<java.util.List<TrainingCourseResponse>> findRegistrable(Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa đang mở đăng ký thành công", trainingCourseService.findRegistrable(authentication));
    }

    @GetMapping("/recipient-candidates")
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreate(authentication, #courseTypeCode)")
    public apiResponse<java.util.List<CourseRecipientCandidateResponse>> recipientCandidates(@RequestParam String courseTypeCode, Authentication authentication) {
        return apiResponse.success("Lấy danh sách tài khoản nhận danh sách đăng ký thành công", trainingCourseService.recipientCandidates(courseTypeCode, authentication));
    }

    @GetMapping("/passing-score-limit")
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreate(authentication, #courseTypeCode)")
    public apiResponse<CoursePassingScoreLimitResponse> passingScoreLimit(@RequestParam String courseTypeCode, Authentication authentication) {
        return apiResponse.success("Lấy điểm đạt tối đa thành công", trainingCourseService.passingScoreLimit(courseTypeCode, authentication));
    }

    @PatchMapping("/{courseCode}/registration")
    @PreAuthorize("@trainingCourseAccessEvaluator.canUpdate(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> setRegistrationOpen(
            @PathVariable String courseCode,
            @Valid @RequestBody CourseRegistrationToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật trạng thái đăng ký thành công", trainingCourseService.setRegistrationOpen(courseCode, request, authentication));
    }

    @GetMapping("/{courseCode}")
    @PreAuthorize("@trainingCourseAccessEvaluator.canRead(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> findById(@PathVariable String courseCode, Authentication authentication) {
        return apiResponse.success("Lấy thông tin khóa huấn luyện thành công", trainingCourseService.findById(courseCode, authentication));
    }

    @PutMapping("/{courseCode}")
    @PreAuthorize("@trainingCourseAccessEvaluator.canUpdate(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> update(
            @PathVariable String courseCode,
            @Valid @RequestBody TrainingCourseUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật khóa huấn luyện thành công", trainingCourseService.update(courseCode, request, authentication));
    }
    @PatchMapping("/{courseCode}/toggle-status")
    @PreAuthorize("@trainingCourseAccessEvaluator.canToggle(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> toggleStatus(
            @PathVariable String courseCode,
            @Valid @RequestBody CourseStatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái khóa huấn luyện thành công", trainingCourseService.toggleStatus(courseCode, request, authentication));
    }

    // Cảnh báo trước khi vô hiệu hóa / bật lại khóa: số chứng nhận bị thu hồi hoặc được mở lại.
    @GetMapping("/{courseCode}/status-impact")
    @PreAuthorize("@trainingCourseAccessEvaluator.canToggle(authentication, #courseCode)")
    public apiResponse<CourseStatusImpactResponse> statusImpact(@PathVariable String courseCode) {
        return apiResponse.success("Lấy tác động đổi trạng thái khóa thành công", trainingCourseService.statusImpact(courseCode));
    }
}
