package phucuong.ht.modules.certificate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.service.CertificateService;

import java.util.List;
import java.util.UUID;

/**
 * Chứng nhận của một huynh trưởng, xem trong hồ sơ huynh trưởng. Khác với danh sách ở menu Chứng nhận,
 * quyền này theo phạm vi quản lý huynh trưởng nên thấy cả chứng nhận thuộc khóa cấp khác và chứng nhận đã thu hồi.
 */
@RestController
@RequestMapping("/api/leaders/{leaderId}/certificates")
@RequiredArgsConstructor
public class LeaderCertificateController {

    private final CertificateService certificateService;

    @GetMapping
    @PreAuthorize("@certificateAccessEvaluator.canReadLeaderCertificates(authentication, #leaderId)")
    public apiResponse<List<CertificateResponse>> findByLeader(@PathVariable UUID leaderId, Authentication authentication) {
        return apiResponse.success("Lấy chứng nhận của huynh trưởng thành công", certificateService.findByLeader(leaderId, authentication));
    }
}
