package phucuong.ht.modules.certificate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateBulkApprovalRequest;
import phucuong.ht.modules.certificate.model.request.CertificateCreateRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRejectRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingBulkApprovalResponse;
import phucuong.ht.modules.certificate.model.request.CertificateUpdateRequest;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.service.CertificateService;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @PostMapping
    @PreAuthorize("@certificateAccessEvaluator.canCreate(authentication, #request.courseCode)")
    public apiResponse<CertificateResponse> create(@Valid @RequestBody CertificateCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo chứng chỉ thành công", certificateService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public apiResponse<Page<CertificateResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách chứng chỉ thành công", certificateService.findAll(request, authentication));
    }

    @GetMapping("/{certificateCode}")
    @PreAuthorize("@certificateAccessEvaluator.canRead(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> findById(@PathVariable String certificateCode, Authentication authentication) {
        return apiResponse.success("Lấy thông tin chứng chỉ thành công", certificateService.findById(certificateCode, authentication));
    }

    @PutMapping("/{certificateCode}")
    @PreAuthorize("@certificateAccessEvaluator.canUpdate(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> update(
            @PathVariable String certificateCode,
            @Valid @RequestBody CertificateUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật chứng chỉ thành công", certificateService.update(certificateCode, request, authentication));
    }
    @PatchMapping("/{certificateCode}/toggle-status")
    @PreAuthorize("@certificateAccessEvaluator.canToggle(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> toggleStatus(
            @PathVariable String certificateCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái chứng chỉ thành công", certificateService.toggleStatus(certificateCode, request, authentication));
    }

    @PostMapping("/{certificateCode}/approve")
    @PreAuthorize("@certificateAccessEvaluator.canApprove(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> approve(
            @PathVariable String certificateCode,
            @RequestBody(required = false) CertificateApproveRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Duyệt chứng chỉ thành công", certificateService.approve(certificateCode, request, authentication));
    }

    @PostMapping("/{certificateCode}/reject")
    @PreAuthorize("@certificateAccessEvaluator.canApprove(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> reject(
            @PathVariable String certificateCode,
            @RequestBody CertificateRejectRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Từ chối chứng chỉ thành công", certificateService.reject(certificateCode, request, authentication));
    }

    @PostMapping("/bulk-approval")
    @PreAuthorize("@certificateAccessEvaluator.canBulkApprove(authentication)")
    public apiResponse<TrainingBulkApprovalResponse> bulkApprove(@Valid @RequestBody CertificateBulkApprovalRequest request, Authentication authentication) {
        return apiResponse.success("Xử lý duyệt hàng loạt hoàn tất", certificateService.bulkApprove(request, authentication));
    }
}
