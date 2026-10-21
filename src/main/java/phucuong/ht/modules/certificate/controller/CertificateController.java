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

    // Thu hồi chứng nhận đã duyệt: hồ sơ của khóa liên kết trở thành không đạt.
    @PostMapping("/{certificateCode}/revoke")
    @PreAuthorize("@certificateAccessEvaluator.canRevoke(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> revoke(
            @PathVariable String certificateCode,
            @RequestBody CertificateRevokeRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Thu hồi chứng nhận thành công", certificateService.revoke(certificateCode, request, authentication));
    }

    // Cấp lại chứng nhận đã bị thu hồi vì lý do khác (không phải do khóa bị vô hiệu hóa); cần quyền cấp lại.
    @PostMapping("/{certificateCode}/reissue")
    @PreAuthorize("@certificateAccessEvaluator.canReissue(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> reissue(
            @PathVariable String certificateCode,
            @RequestBody CertificateReissueRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cấp lại chứng nhận thành công", certificateService.reissue(certificateCode, request, authentication));
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
