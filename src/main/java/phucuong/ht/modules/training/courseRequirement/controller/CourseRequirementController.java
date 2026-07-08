package phucuong.ht.modules.training.courseRequirement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.training.courseRequirement.model.request.CourseRequirementCreateRequest;
import phucuong.ht.modules.training.courseRequirement.model.request.CourseRequirementUpdateRequest;
import phucuong.ht.modules.training.courseRequirement.model.response.CourseRequirementResponse;
import phucuong.ht.modules.training.courseRequirement.service.CourseRequirementService;

@RestController
@RequestMapping("/api/training/requirements")
@RequiredArgsConstructor
public class CourseRequirementController {

    private final CourseRequirementService courseRequirementService;

    @PostMapping
    @PreAuthorize("@courseRequirementAccessEvaluator.canCreate(authentication, #request.courseType)")
    public apiResponse<CourseRequirementResponse> create(@Valid @RequestBody CourseRequirementCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo requirement thành công", courseRequirementService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@courseRequirementAccessEvaluator.canList(authentication)")
    public apiResponse<Page<CourseRequirementResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách requirement thành công", courseRequirementService.findAll(request, authentication));
    }

    @GetMapping("/{requirementCode}")
    @PreAuthorize("@courseRequirementAccessEvaluator.canRead(authentication, #requirementCode)")
    public apiResponse<CourseRequirementResponse> findById(@PathVariable String requirementCode, Authentication authentication) {
        return apiResponse.success("Lấy requirement thành công", courseRequirementService.findById(requirementCode, authentication));
    }

    @PutMapping("/{requirementCode}")
    @PreAuthorize("@courseRequirementAccessEvaluator.canUpdate(authentication, #requirementCode)")
    public apiResponse<CourseRequirementResponse> update(
            @PathVariable String requirementCode,
            @Valid @RequestBody CourseRequirementUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật requirement thành công", courseRequirementService.update(requirementCode, request, authentication));
    }

    @DeleteMapping("/{requirementCode}")
    @PreAuthorize("@courseRequirementAccessEvaluator.canDelete(authentication, #requirementCode)")
    public apiResponse<Void> delete(@PathVariable String requirementCode) {
        courseRequirementService.delete(requirementCode);
        return apiResponse.success("Xóa requirement thành công", null);
    }

    @PatchMapping("/{requirementCode}/toggle-status")
    @PreAuthorize("@courseRequirementAccessEvaluator.canUpdate(authentication, #requirementCode)")
    public apiResponse<CourseRequirementResponse> toggleStatus(
            @PathVariable String requirementCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái requirement thành công", courseRequirementService.toggleStatus(requirementCode, request, authentication));
    }
}
