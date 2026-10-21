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

    @PostMapping
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreate(authentication, #request.courseTypeCode)")
    public apiResponse<TrainingCourseResponse> create(@Valid @RequestBody TrainingCourseCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo khóa huấn luyện thành công", trainingCourseService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@trainingCourseAccessEvaluator.canList(authentication)")
    public apiResponse<Page<TrainingCourseResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa huấn luyện thành công", trainingCourseService.findAll(request, authentication));
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
