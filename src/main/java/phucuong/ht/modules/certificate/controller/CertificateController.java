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
import phucuong.ht.modules.certificate.model.request.CertificateCreateRequest;
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
    public apiResponse<Page<CertificateResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách chứng chỉ thành công", certificateService.findAll(request, authentication));
    }

    @GetMapping("/{certificateCode}")
    @PreAuthorize("@certificateAccessEvaluator.canRead(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> findById(@PathVariable String certificateCode) {
        return apiResponse.success("Lấy chứng chỉ thành công", certificateService.findById(certificateCode));
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

    @DeleteMapping("/{certificateCode}")
    @PreAuthorize("@certificateAccessEvaluator.canDelete(authentication, #certificateCode)")
    public apiResponse<Void> delete(@PathVariable String certificateCode) {
        certificateService.delete(certificateCode);
        return apiResponse.success("Xóa chứng chỉ thành công", null);
    }

    @PatchMapping("/{certificateCode}/toggle-status")
    @PreAuthorize("@certificateAccessEvaluator.canUpdate(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> toggleStatus(
            @PathVariable String certificateCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái chứng chỉ thành công", certificateService.toggleStatus(certificateCode, request, authentication));
    }

    @PostMapping("/{certificateCode}/approve")
    @PreAuthorize("@certificateAccessEvaluator.canApprove(authentication, #certificateCode)")
    public apiResponse<CertificateResponse> approve(@PathVariable String certificateCode, Authentication authentication) {
        return apiResponse.success("Duyệt cấp chứng chỉ thành công", certificateService.approve(certificateCode, authentication));
    }
}
