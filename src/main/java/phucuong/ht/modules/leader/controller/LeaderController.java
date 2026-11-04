package phucuong.ht.modules.leader.controller;

import phucuong.ht.modules.privacy.model.PrivacyConsentSource;
import phucuong.ht.modules.privacy.service.PrivacyConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderTransferRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.rankHistory.model.response.RankHistoryResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaders")
@RequiredArgsConstructor
public class LeaderController {

    private final PrivacyConsentService privacyConsent;

    private final LeaderService leaderService;
    private final phucuong.ht.common.excel.job.ExcelJobService excelJobService;
    private final phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;

    @PostMapping
    @PreAuthorize("@leaderAccessEvaluator.canCreate(authentication)")
    public apiResponse<LeaderResponse> create(@Valid @RequestBody LeaderCreateRequest request, Authentication authentication) {
        LeaderResponse created = leaderService.create(request, authentication);
        privacyConsent.recordAttestation(authentication, created.getId(), PrivacyConsentSource.LEADER_FORM);
        return apiResponse.success("Tạo Huynh Trưởng thành công", created);
    }

    @GetMapping
    @PreAuthorize("@leaderAccessEvaluator.canList(authentication)")
    public apiResponse<Page<LeaderResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách Huynh Trưởng thành công", leaderService.findAll(request, authentication));
    }

    // Tải file Excel mẫu để tự tạo file import (cùng quyền với tạo huynh trưởng).
    @GetMapping("/import-template")
    @PreAuthorize("@leaderAccessEvaluator.canCreate(authentication)")
    public org.springframework.http.ResponseEntity<byte[]> importTemplate(Authentication authentication) {
        return phucuong.ht.common.excel.ExcelResponses.xlsx(
                phucuong.ht.common.excel.ImportTemplates.leaders(leaderLevelService.selectableLevelNames(authentication)), "mau-import-ho-so-huynh-truong");
    }

    // Import hồ sơ huynh trưởng từ Excel chạy nền (cùng quyền và phạm vi với tạo huynh trưởng). Có dòng lỗi thì không lưu gì.
    // Bắt buộc có Idempotency-Key: gửi lại cùng key trả lại đúng tác vụ cũ, không import hai lần.
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@leaderAccessEvaluator.canCreate(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> importLeaders(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestPart(value = "images", required = false) org.springframework.web.multipart.MultipartFile images,
            @RequestParam(value = "subjectConsentConfirmed", required = false) Boolean subjectConsentConfirmed,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        privacyConsent.requireConfirmed(subjectConsentConfirmed);
        var accepted = excelJobService.createImport(
                phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", file, images, idempotencyKey, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.LEADER_IMPORT);
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(accepted);
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/export")
    @PreAuthorize("@leaderAccessEvaluator.canList(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> export(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_LEADERS, request, idempotencyKey, authentication));
    }

    @GetMapping("/account-candidates")
    @PreAuthorize("@leaderAccessEvaluator.canList(authentication)")
    public apiResponse<List<LeaderResponse>> accountCandidates(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách Huynh Trưởng chưa có tài khoản thành công", leaderService.findAccountCandidates(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@leaderAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<LeaderResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin Huynh Trưởng thành công", leaderService.findById(id, authentication));
    }

    @GetMapping("/{id}/rank-history")
    @PreAuthorize("@leaderAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<List<RankHistoryResponse>> rankHistory(@PathVariable UUID id) {
        return apiResponse.success("Lấy lịch sử cấp bậc Huynh Trưởng thành công", leaderService.findRankHistory(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@leaderAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<LeaderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody LeaderUpdateRequest request,
            Authentication authentication
    ) {
        LeaderResponse updated = leaderService.update(id, request, authentication);
        privacyConsent.recordAttestation(authentication, id, PrivacyConsentSource.LEADER_FORM);
        return apiResponse.success("Cập nhật Huynh Trưởng thành công", updated);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("@leaderAccessEvaluator.canTransfer(authentication, #id)")
    public apiResponse<LeaderResponse> transfer(
            @PathVariable UUID id,
            @Valid @RequestBody LeaderTransferRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Chuyển giáo xứ cho huynh trưởng thành công", leaderService.transfer(id, request, authentication));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@leaderAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<LeaderResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái Huynh Trưởng thành công", leaderService.toggleStatus(id, request, authentication));
    }
}
