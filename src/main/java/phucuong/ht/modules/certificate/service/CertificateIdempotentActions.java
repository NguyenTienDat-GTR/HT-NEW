package phucuong.ht.modules.certificate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.idempotency.IdempotencyService;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateBulkApprovalRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRejectRequest;
import phucuong.ht.modules.certificate.model.request.CertificateReissueRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest;

/**
 * Bọc duyệt / từ chối / thu hồi / cấp lại chứng nhận bằng Idempotency-Key. Cả bản ghi key và thao tác nằm trong MỘT transaction:
 * gửi lại cùng key (bấm đúp, mạng chập chờn, hai tab) chỉ thực hiện một lần và nhận lại đúng kết quả cũ. Việc nhiều người khác nhau
 * cùng thao tác một chứng nhận được chặn riêng bằng khóa hàng trong {@link CertificateService}.
 */
@Service
@RequiredArgsConstructor
public class CertificateIdempotentActions {

    private final CertificateService certificateService;
    private final IdempotencyService idempotencyService;
    private final securityUtils securityUtils;

    @Transactional
    public IdempotencyService.Outcome approve(String code, CertificateApproveRequest request, String key, Authentication authentication) {
        return idempotencyService.execute(owner(authentication), "certificate.approve:" + code, key, request,
                () -> certificateService.approve(code, request, authentication));
    }

    @Transactional
    public IdempotencyService.Outcome reject(String code, CertificateRejectRequest request, String key, Authentication authentication) {
        return idempotencyService.execute(owner(authentication), "certificate.reject:" + code, key, request,
                () -> certificateService.reject(code, request, authentication));
    }

    @Transactional
    public IdempotencyService.Outcome revoke(String code, CertificateRevokeRequest request, String key, Authentication authentication) {
        return idempotencyService.execute(owner(authentication), "certificate.revoke:" + code, key, request,
                () -> certificateService.revoke(code, request, authentication));
    }

    @Transactional
    public IdempotencyService.Outcome reissue(String code, CertificateReissueRequest request, String key, Authentication authentication) {
        return idempotencyService.execute(owner(authentication), "certificate.reissue:" + code, key, request,
                () -> certificateService.reissue(code, request, authentication));
    }

    @Transactional
    public IdempotencyService.Outcome bulkApprove(CertificateBulkApprovalRequest request, String key, Authentication authentication) {
        return idempotencyService.execute(owner(authentication), "certificate.bulk-approval", key, request,
                () -> certificateService.bulkApprove(request, authentication));
    }

    private String owner(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        return username == null ? "anonymous" : username;
    }
}
