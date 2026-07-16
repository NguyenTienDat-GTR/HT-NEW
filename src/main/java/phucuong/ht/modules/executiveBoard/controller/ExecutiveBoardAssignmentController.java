package phucuong.ht.modules.executiveBoard.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentCreateRequest;
import phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentUpdateRequest;
import phucuong.ht.modules.executiveBoard.model.response.ExecutiveBoardAssignmentResponse;
import phucuong.ht.modules.executiveBoard.service.ExecutiveBoardAssignmentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/executive-board/assignments")
@RequiredArgsConstructor
public class ExecutiveBoardAssignmentController {

    private final ExecutiveBoardAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("@executiveBoardAssignmentAccessEvaluator.canCreate(authentication, #request.unitType)")
    public apiResponse<ExecutiveBoardAssignmentResponse> create(@Valid @RequestBody ExecutiveBoardAssignmentCreateRequest request, Authentication authentication) {
        return apiResponse.success("Gán leader vào chức vụ thành công", assignmentService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@executiveBoardAssignmentAccessEvaluator.canList(authentication)")
    public apiResponse<Page<ExecutiveBoardAssignmentResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách phân công chức vụ thành công", assignmentService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@executiveBoardAssignmentAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<ExecutiveBoardAssignmentResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy phân công chức vụ thành công", assignmentService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@executiveBoardAssignmentAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<ExecutiveBoardAssignmentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ExecutiveBoardAssignmentUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật phân công chức vụ thành công", assignmentService.update(id, request, authentication));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@executiveBoardAssignmentAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<ExecutiveBoardAssignmentResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái phân công chức vụ thành công", assignmentService.toggleStatus(id, request, authentication));
    }
}
