package phucuong.ht.modules.deanery.controller;

import phucuong.ht.modules.privacy.model.PrivacyConsentSource;
import phucuong.ht.modules.privacy.service.PrivacyConsentService;
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
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.service.DeaneryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deaneries")
@RequiredArgsConstructor
public class DeaneryController {

    private final PrivacyConsentService privacyConsent;

    private final DeaneryService deaneryService;
    private final phucuong.ht.common.excel.job.ExcelJobService excelJobService;

    @PostMapping
    @PreAuthorize("@deaneryAccessEvaluator.canCreate(authentication)")
    public apiResponse<DeaneryResponse> create(@Valid @RequestBody DeaneryCreateRequest request, Authentication authentication) {
        DeaneryResponse created = deaneryService.create(request, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.ORG_FORM);
        return apiResponse.success("Tạo giáo hạt thành công", created);
    }

    @GetMapping
    @PreAuthorize("@deaneryAccessEvaluator.canList(authentication)")
    public apiResponse<Page<DeaneryResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách giáo hạt thành công", deaneryService.findAll(request, authentication));
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/export")
    @PreAuthorize("@deaneryAccessEvaluator.canList(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> export(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_DEANERIES, request, idempotencyKey, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@deaneryAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<DeaneryResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin giáo hạt thành công", deaneryService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@deaneryAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<DeaneryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DeaneryUpdateRequest request,
            Authentication authentication
    ) {
        DeaneryResponse updated = deaneryService.update(id, request, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.ORG_FORM);
        return apiResponse.success("Cập nhật giáo hạt thành công", updated);
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

    @GetMapping("/{id}/toggle-impact")
    @PreAuthorize("@deaneryAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<UnitToggleImpactResponse> toggleImpact(@PathVariable UUID id) {
        return apiResponse.success("Lấy tác động khi đổi trạng thái giáo hạt thành công", deaneryService.toggleImpact(id));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("@deaneryAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<List<UnitStatusHistoryResponse>> statusHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return apiResponse.success("Lấy lịch sử trạng thái giáo hạt thành công", deaneryService.statusHistory(id, page, size));
    }
}
