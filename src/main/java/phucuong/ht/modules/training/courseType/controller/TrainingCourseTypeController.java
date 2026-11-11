package phucuong.ht.modules.training.courseType.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeCreateRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.training.courseType.model.response.PrerequisiteOptionResponse;
import phucuong.ht.modules.training.courseType.model.response.TrainingCourseTypeResponse;
import phucuong.ht.modules.training.courseType.service.TrainingCourseTypeService;

@RestController
@RequestMapping("/api/training/course-types")
@RequiredArgsConstructor
public class TrainingCourseTypeController {

    private final TrainingCourseTypeService trainingCourseTypeService;

    @PostMapping
    @PreAuthorize("@trainingCourseTypeAccessEvaluator.canCreate(authentication, #request.managementUnitType)")
    public apiResponse<TrainingCourseTypeResponse> create(@Valid @RequestBody TrainingCourseTypeCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo loại khóa huấn luyện thành công", trainingCourseTypeService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@trainingCourseTypeAccessEvaluator.canList(authentication)")
    public apiResponse<Page<TrainingCourseTypeResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách loại khóa huấn luyện thành công", trainingCourseTypeService.findAll(request, authentication));
    }

    // Lựa chọn khóa tiên quyết: loại khóa cấp giáo phận chọn được cả loại khóa cấp giáo hạt.
    @GetMapping("/prerequisite-options")
    @PreAuthorize("@trainingCourseTypeAccessEvaluator.canPickPrerequisite(authentication, #managementUnitType)")
    public apiResponse<java.util.List<PrerequisiteOptionResponse>> prerequisiteOptions(
            @RequestParam(required = false) UnitType managementUnitType,
            @RequestParam(required = false) String forTypeCode,
            Authentication authentication
    ) {
        return apiResponse.success("Lấy danh sách khóa tiên quyết thành công",
                trainingCourseTypeService.prerequisiteOptions(managementUnitType, forTypeCode, authentication));
    }

    @GetMapping("/{typeCode}")
    @PreAuthorize("@trainingCourseTypeAccessEvaluator.canRead(authentication, #typeCode)")
    public apiResponse<TrainingCourseTypeResponse> findById(@PathVariable String typeCode, Authentication authentication) {
        return apiResponse.success("Lấy loại khóa huấn luyện thành công", trainingCourseTypeService.findById(typeCode, authentication));
    }

    @PutMapping("/{typeCode}")
    @PreAuthorize("@trainingCourseTypeAccessEvaluator.canUpdate(authentication, #typeCode)")
    public apiResponse<TrainingCourseTypeResponse> update(
            @PathVariable String typeCode,
            @Valid @RequestBody TrainingCourseTypeUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật loại khóa huấn luyện thành công", trainingCourseTypeService.update(typeCode, request, authentication));
    }

    @PatchMapping("/{typeCode}/toggle-status")
    @PreAuthorize("@trainingCourseTypeAccessEvaluator.canToggle(authentication, #typeCode)")
    public apiResponse<TrainingCourseTypeResponse> toggleStatus(
            @PathVariable String typeCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái loại khóa huấn luyện thành công", trainingCourseTypeService.toggleStatus(typeCode, request, authentication));
    }
}
