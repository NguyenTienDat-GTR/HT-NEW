package phucuong.ht.modules.badge.config.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.badge.config.model.request.BadgeConfigUpsertRequest;
import phucuong.ht.modules.badge.config.model.response.BadgeConfigResponse;
import phucuong.ht.modules.badge.config.service.BadgeConfigService;
import phucuong.ht.modules.certificate.config.model.response.CertificateFieldCatalogItem;

import java.util.List;
import java.util.UUID;

// Thư viện cấu hình thẻ tham dự khóa huấn luyện (khổ thẻ, mặt trước / sau, ảnh nền, bố cục mail merge).
@RestController
@RequestMapping("/api/badges/config")
@RequiredArgsConstructor
public class BadgeConfigController {

    private final BadgeConfigService service;

    @GetMapping
    @PreAuthorize("@badgeConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<Page<BadgeConfigResponse>> list(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy thư viện cấu hình thẻ tham dự thành công", service.search(request, authentication));
    }

    // Toàn bộ cấu hình xem / dùng được ở dạng gọn (không phân trang), cho ô chọn cấu hình.
    @GetMapping("/options")
    @PreAuthorize("@badgeConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<BadgeConfigResponse>> options(Authentication authentication) {
        return apiResponse.success("Lấy danh sách cấu hình thẻ tham dự thành công", service.options(authentication));
    }

    @GetMapping("/fields")
    @PreAuthorize("@badgeConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<CertificateFieldCatalogItem>> fields() {
        return apiResponse.success("Lấy danh sách trường thẻ tham dự thành công", service.fieldCatalog());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@badgeConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<BadgeConfigResponse> detail(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy chi tiết cấu hình thẻ tham dự thành công", service.get(id, authentication));
    }

    @PostMapping
    @PreAuthorize("@badgeConfigAccessEvaluator.canCreate(authentication)")
    public apiResponse<BadgeConfigResponse> create(@Valid @RequestBody BadgeConfigUpsertRequest request, Authentication authentication) {
        return apiResponse.success("Tạo cấu hình thẻ tham dự thành công", service.create(request, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@badgeConfigAccessEvaluator.canUpdate(authentication)")
    public apiResponse<BadgeConfigResponse> update(@PathVariable UUID id, @Valid @RequestBody BadgeConfigUpsertRequest request,
                                                   Authentication authentication) {
        return apiResponse.success("Lưu cấu hình thẻ tham dự thành công", service.update(id, request, authentication));
    }

    @PutMapping("/{id}/default")
    @PreAuthorize("@badgeConfigAccessEvaluator.canUpdate(authentication)")
    public apiResponse<BadgeConfigResponse> setDefault(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Đã đặt làm cấu hình chung", service.setDefault(id, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@badgeConfigAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable UUID id, Authentication authentication) {
        service.delete(id, authentication);
        return apiResponse.<Void>success("Xóa cấu hình thẻ tham dự thành công", null);
    }
}
