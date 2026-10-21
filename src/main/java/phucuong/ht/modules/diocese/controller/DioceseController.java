package phucuong.ht.modules.diocese.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.UnitStatusHistoryResponse;
import phucuong.ht.common.dto.UnitToggleImpactResponse;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.model.response.DioceseResponse;
import phucuong.ht.modules.diocese.service.DioceseService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dioceses")
@RequiredArgsConstructor
public class DioceseController {

    private final DioceseService dioceseService;

    @PostMapping
    @PreAuthorize("@dioceseAccessEvaluator.canCreate(authentication)")
    public apiResponse<DioceseResponse> create(@Valid @RequestBody DioceseCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo giáo phận thành công", dioceseService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@dioceseAccessEvaluator.canList(authentication)")
    public apiResponse<Page<DioceseResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách giáo phận thành công", dioceseService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@dioceseAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<DioceseResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin giáo phận thành công", dioceseService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@dioceseAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<DioceseResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DioceseUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật giáo phận thành công", dioceseService.update(id, request, authentication));
    }
    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@dioceseAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<DioceseResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái giáo phận thành công", dioceseService.toggleStatus(id, request, authentication));
    }

    @GetMapping("/{id}/toggle-impact")
    @PreAuthorize("@dioceseAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<UnitToggleImpactResponse> toggleImpact(@PathVariable UUID id) {
        return apiResponse.success("Lấy tác động khi đổi trạng thái giáo phận thành công", dioceseService.toggleImpact(id));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("@dioceseAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<List<UnitStatusHistoryResponse>> statusHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return apiResponse.success("Lấy lịch sử trạng thái giáo phận thành công", dioceseService.statusHistory(id, page, size));
    }
}
