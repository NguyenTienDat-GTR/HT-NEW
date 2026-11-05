package phucuong.ht.modules.parish.controller;

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
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishParentChangeRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;
import phucuong.ht.modules.parish.model.response.ParishResponse;
import phucuong.ht.modules.parish.service.ParishService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parishes")
@RequiredArgsConstructor
public class ParishController {

    private final PrivacyConsentService privacyConsent;

    private final ParishService parishService;
    private final phucuong.ht.common.excel.job.ExcelJobService excelJobService;
    private final phucuong.ht.modules.parish.service.ParishImportService parishImportService;

    @PostMapping
    @PreAuthorize("@parishAccessEvaluator.canCreate(authentication)")
    public apiResponse<ParishResponse> create(@Valid @RequestBody ParishCreateRequest request, Authentication authentication) {
        ParishResponse created = parishService.create(request, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.ORG_FORM);
        return apiResponse.success("Tạo giáo xứ thành công", created);
    }

    @GetMapping
    @PreAuthorize("@parishAccessEvaluator.canList(authentication)")
    public apiResponse<Page<ParishResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách giáo xứ thành công", parishService.findAll(request, authentication));
    }

    // Tải file Excel mẫu để tự tạo file import (cùng quyền với tạo giáo xứ); danh sách chọn giáo hạt lấy từ các giáo hạt trong phạm vi tài khoản.
    @GetMapping("/import-template")
    @PreAuthorize("@parishAccessEvaluator.canCreate(authentication)")
    public org.springframework.http.ResponseEntity<byte[]> importTemplate(Authentication authentication) {
        return phucuong.ht.common.excel.ExcelResponses.xlsx(
                phucuong.ht.common.excel.ImportTemplates.parishes(parishImportService.selectableDeaneryNames(authentication)), "mau-import-giao-xu");
    }

    // Import giáo xứ từ Excel chạy nền (cùng quyền và phạm vi với tạo giáo xứ). Có dòng lỗi thì không lưu gì.
    // Bắt buộc có Idempotency-Key: gửi lại cùng key trả lại đúng tác vụ cũ, không import hai lần.
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@parishAccessEvaluator.canCreate(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> importParishes(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "subjectConsentConfirmed", required = false) Boolean subjectConsentConfirmed,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        privacyConsent.requireConfirmed(subjectConsentConfirmed);
        var accepted = excelJobService.createImport(
                phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_PARISHES, "", file, null, idempotencyKey, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.ORG_FORM);
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(accepted);
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/export")
    @PreAuthorize("@parishAccessEvaluator.canList(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> export(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_PARISHES, request, idempotencyKey, authentication));
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
        ParishResponse updated = parishService.update(id, request, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.ORG_FORM);
        return apiResponse.success("Cập nhật giáo xứ thành công", updated);
    }
    // Đổi giáo xứ cha của giáo họ / giáo điểm: admin giáo hạt / giáo phận trong phạm vi (cùng quyền tạo giáo xứ).
    @PatchMapping("/{id}/parent")
    @PreAuthorize("@parishAccessEvaluator.canChangeParent(authentication, #id)")
    public apiResponse<ParishResponse> changeParent(
            @PathVariable UUID id,
            @Valid @RequestBody ParishParentChangeRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi giáo xứ cha thành công", parishService.changeParent(id, request, authentication));
    }

    // Nâng giáo họ / giáo điểm thành giáo xứ độc lập: cùng quyền với đổi giáo xứ cha.
    @PatchMapping("/{id}/promote")
    @PreAuthorize("@parishAccessEvaluator.canChangeParent(authentication, #id)")
    public apiResponse<ParishResponse> promoteToParish(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Chuyển thành giáo xứ thành công", parishService.promoteToParish(id, authentication));
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

    @GetMapping("/{id}/toggle-impact")
    @PreAuthorize("@parishAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<UnitToggleImpactResponse> toggleImpact(@PathVariable UUID id) {
        return apiResponse.success("Lấy tác động khi đổi trạng thái giáo xứ thành công", parishService.toggleImpact(id));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("@parishAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<List<UnitStatusHistoryResponse>> statusHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return apiResponse.success("Lấy lịch sử trạng thái giáo xứ thành công", parishService.statusHistory(id, page, size));
    }
}
