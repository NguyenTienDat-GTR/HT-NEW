package phucuong.ht.modules.parish.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;
import phucuong.ht.modules.parish.model.response.ParishResponse;
import phucuong.ht.modules.parish.service.ParishService;

import java.util.UUID;

@RestController
@RequestMapping("/api/parishes")
@RequiredArgsConstructor
public class ParishController {

    private final ParishService parishService;

    @PostMapping
    @PreAuthorize("@parishAccessEvaluator.canCreate(authentication)")
    public apiResponse<ParishResponse> create(@Valid @RequestBody ParishCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo giáo xứ thành công", parishService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@parishAccessEvaluator.canList(authentication)")
    public apiResponse<Page<ParishResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách giáo xứ thành công", parishService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@parishAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<ParishResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin giáo xứ thành công", parishService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@parishAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<ParishResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ParishUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật giáo xứ thành công", parishService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@parishAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable UUID id, Authentication authentication) {
        parishService.delete(id, authentication);
        return apiResponse.success("Xóa giáo xứ thành công", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@parishAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<ParishResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái giáo xứ thành công", parishService.toggleStatus(id, request, authentication));
    }
}
