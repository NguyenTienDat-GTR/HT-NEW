package phucuong.ht.modules.deanery.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.service.DeaneryService;

import java.util.UUID;

@RestController
@RequestMapping("/api/deaneries")
@RequiredArgsConstructor
public class DeaneryController {

    private final DeaneryService deaneryService;

    @PostMapping
    @PreAuthorize("@deaneryAccessEvaluator.canCreate(authentication)")
    public apiResponse<DeaneryResponse> create(@Valid @RequestBody DeaneryCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo giáo hạt thành công", deaneryService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@deaneryAccessEvaluator.canList(authentication)")
    public apiResponse<Page<DeaneryResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách giáo hạt thành công", deaneryService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@deaneryAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<DeaneryResponse> findById(@PathVariable UUID id) {
        return apiResponse.success("Lấy thông tin giáo hạt thành công", deaneryService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@deaneryAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<DeaneryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DeaneryUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật giáo hạt thành công", deaneryService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@deaneryAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable UUID id, Authentication authentication) {
        deaneryService.delete(id, authentication);
        return apiResponse.success("Xóa giáo hạt thành công", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@deaneryAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<DeaneryResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái giáo hạt thành công", deaneryService.toggleStatus(id, request, authentication));
    }
}
