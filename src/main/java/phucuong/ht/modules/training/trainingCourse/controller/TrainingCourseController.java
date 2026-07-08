package phucuong.ht.modules.training.trainingCourse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
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
    @PreAuthorize("@trainingCourseAccessEvaluator.canCreate(authentication, #request.courseType)")
    public apiResponse<TrainingCourseResponse> create(@Valid @RequestBody TrainingCourseCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo khóa huấn luyện thành công", trainingCourseService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@trainingCourseAccessEvaluator.canList(authentication)")
    public apiResponse<Page<TrainingCourseResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa huấn luyện thành công", trainingCourseService.findAll(request, authentication));
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

    @DeleteMapping("/{courseCode}")
    @PreAuthorize("@trainingCourseAccessEvaluator.canDelete(authentication, #courseCode)")
    public apiResponse<Void> delete(@PathVariable String courseCode) {
        trainingCourseService.delete(courseCode);
        return apiResponse.success("Xóa khóa huấn luyện thành công", null);
    }

    @PatchMapping("/{courseCode}/toggle-status")
    @PreAuthorize("@trainingCourseAccessEvaluator.canUpdate(authentication, #courseCode)")
    public apiResponse<TrainingCourseResponse> toggleStatus(
            @PathVariable String courseCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái khóa huấn luyện thành công", trainingCourseService.toggleStatus(courseCode, request, authentication));
    }
}
