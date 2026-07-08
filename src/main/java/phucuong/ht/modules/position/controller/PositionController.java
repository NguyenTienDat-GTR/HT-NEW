package phucuong.ht.modules.position.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.position.model.request.PositionCreateRequest;
import phucuong.ht.modules.position.model.request.PositionUpdateRequest;
import phucuong.ht.modules.position.model.response.PositionResponse;
import phucuong.ht.modules.position.service.PositionService;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @PostMapping
    @PreAuthorize("@positionAccessEvaluator.canCreate(authentication, #request.positionType)")
    public apiResponse<PositionResponse> create(@Valid @RequestBody PositionCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo chức vụ thành công", positionService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@positionAccessEvaluator.canList(authentication)")
    public apiResponse<Page<PositionResponse>> findAll(@ModelAttribute BaseSearchRequest request) {
        return apiResponse.success("Lấy danh sách chức vụ thành công", positionService.findAll(request));
    }

    @GetMapping("/{positionCode}")
    @PreAuthorize("@positionAccessEvaluator.canRead(authentication, #positionCode)")
    public apiResponse<PositionResponse> findById(@PathVariable String positionCode) {
        return apiResponse.success("Lấy chức vụ thành công", positionService.findById(positionCode));
    }

    @PutMapping("/{positionCode}")
    @PreAuthorize("@positionAccessEvaluator.canUpdate(authentication, #positionCode)")
    public apiResponse<PositionResponse> update(
            @PathVariable String positionCode,
            @Valid @RequestBody PositionUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật chức vụ thành công", positionService.update(positionCode, request, authentication));
    }

    @DeleteMapping("/{positionCode}")
    @PreAuthorize("@positionAccessEvaluator.canDelete(authentication, #positionCode)")
    public apiResponse<Void> delete(@PathVariable String positionCode) {
        positionService.delete(positionCode);
        return apiResponse.success("Xóa chức vụ thành công", null);
    }

    @PatchMapping("/{positionCode}/toggle-status")
    @PreAuthorize("@positionAccessEvaluator.canUpdate(authentication, #positionCode)")
    public apiResponse<PositionResponse> toggleStatus(
            @PathVariable String positionCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái chức vụ thành công", positionService.toggleStatus(positionCode, request, authentication));
    }
}
