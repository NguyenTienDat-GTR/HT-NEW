package phucuong.ht.modules.certificate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateBulkApprovalRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRejectRequest;
import phucuong.ht.modules.certificate.model.request.CertificateReissueRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingBulkApprovalResponse;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.service.CertificateService;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final phucuong.ht.common.excel.job.ExcelJobService excelJobService;
    private final phucuong.ht.modules.certificate.service.CertificateIdempotentActions certificateActions;

    @GetMapping
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public apiResponse<Page<CertificateResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách chứng chỉ thành công", certificateService.findAll(request, authentication));
    }

    // Xuất Excel chạy nền theo đúng bộ lọc/tìm kiếm/sắp xếp của danh sách; trả 202 + tác vụ, theo dõi tại /api/excel-jobs.
    @org.springframework.web.bind.annotation.PostMapping("/export")
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<phucuong.ht.common.excel.job.ExcelJobResponse>> export(
            BaseSearchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return phucuong.ht.common.excel.job.ExcelJobResponses.accepted(
                excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_CERTIFICATES, request, idempotencyKey, authentication));
    }

    // Các năm cấp có chứng nhận (cho bộ lọc năm cấp), mỗi phần tử { "year": 2026 }.
    @GetMapping("/issue-years")
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public apiResponse<java.util.List<java.util.Map<String, Integer>>> issueYears(Authentication authentication) {
        return apiResponse.success("Lấy danh sách năm cấp thành công",
                certificateService.issueYears(authentication).stream().map(year -> java.util.Map.of("year", year)).toList());
    }

    @GetMapping("/{certificateCode}")
    @PreAuthorize("@certificateAccessEvaluator.canRead(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> findById(@PathVariable String certificateCode, Authentication authentication) {
        return apiResponse.success("Lấy thông tin chứng chỉ thành công", certificateService.findById(certificateCode, authentication));
    }

    // Thu hồi chứng nhận đã duyệt: hồ sơ của khóa liên kết trở thành không đạt.
    @PostMapping("/{certificateCode}/revoke")
    @PreAuthorize("@certificateAccessEvaluator.canRevoke(authentication, #certificateCode)")
    public org.springframework.http.ResponseEntity<apiResponse<Object>> revoke(
            @PathVariable String certificateCode,
            @RequestBody CertificateRevokeRequest request,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return respond("Thu hồi chứng nhận thành công", certificateActions.revoke(certificateCode, request, idempotencyKey, authentication));
    }

    // Cấp lại chứng nhận đã bị thu hồi vì lý do khác (không phải do khóa bị vô hiệu hóa); cần quyền cấp lại.
    @PostMapping("/{certificateCode}/reissue")
    @PreAuthorize("@certificateAccessEvaluator.canReissue(authentication, #certificateCode)")
    public org.springframework.http.ResponseEntity<apiResponse<Object>> reissue(
            @PathVariable String certificateCode,
            @RequestBody CertificateReissueRequest request,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return respond("Cấp lại chứng nhận thành công", certificateActions.reissue(certificateCode, request, idempotencyKey, authentication));
    }

    @PostMapping("/{certificateCode}/approve")
    @PreAuthorize("@certificateAccessEvaluator.canApprove(authentication, #certificateCode)")
    public org.springframework.http.ResponseEntity<apiResponse<Object>> approve(
            @PathVariable String certificateCode,
            @RequestBody(required = false) CertificateApproveRequest request,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return respond("Duyệt chứng chỉ thành công", certificateActions.approve(certificateCode, request, idempotencyKey, authentication));
    }

    @PostMapping("/{certificateCode}/reject")
    @PreAuthorize("@certificateAccessEvaluator.canApprove(authentication, #certificateCode)")
    public org.springframework.http.ResponseEntity<apiResponse<Object>> reject(
            @PathVariable String certificateCode,
            @RequestBody CertificateRejectRequest request,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return respond("Từ chối chứng chỉ thành công", certificateActions.reject(certificateCode, request, idempotencyKey, authentication));
    }

    @PostMapping("/bulk-approval")
    @PreAuthorize("@certificateAccessEvaluator.canBulkApprove(authentication)")
    public org.springframework.http.ResponseEntity<apiResponse<Object>> bulkApprove(
            @Valid @RequestBody CertificateBulkApprovalRequest request,
            @RequestHeader(value = phucuong.ht.common.idempotency.IdempotencyService.HEADER, required = false) String idempotencyKey,
            Authentication authentication) {
        return respond("Xử lý duyệt hàng loạt hoàn tất", certificateActions.bulkApprove(request, idempotencyKey, authentication));
    }

    // Gửi lại cùng Idempotency-Key nhận đúng kết quả cũ, kèm header Idempotent-Replay: true (thao tác không chạy lại).
    private org.springframework.http.ResponseEntity<apiResponse<Object>> respond(String message, phucuong.ht.common.idempotency.IdempotencyService.Outcome outcome) {
        org.springframework.http.ResponseEntity.BodyBuilder builder = org.springframework.http.ResponseEntity.ok();
        if (outcome.replayed()) {
            builder.header("Idempotent-Replay", "true");
        }
        return builder.body(apiResponse.success(message, outcome.body()));
    }
}
