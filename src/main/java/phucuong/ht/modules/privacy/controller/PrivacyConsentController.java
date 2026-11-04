package phucuong.ht.modules.privacy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.privacy.model.PrivacyConsentSource;
import phucuong.ht.modules.privacy.model.SelfConsentRequest;
import phucuong.ht.modules.privacy.service.PrivacyConsentService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrivacyConsentController {

    private final PrivacyConsentService service;

    /** Công khai: phiên bản chính sách hiện hành (trang chính sách hiển thị được khi chưa đăng nhập). */
    @GetMapping("/privacy/policy")
    public apiResponse<Map<String, String>> policy() {
        return apiResponse.success("Lấy phiên bản chính sách thành công", Map.of("policyVersion", service.policyVersion()));
    }

    @GetMapping("/auth/me/privacy-consent")
    public apiResponse<PrivacyConsentService.Status> status(Authentication authentication) {
        return apiResponse.success("Lấy trạng thái đồng ý thành công", service.status(authentication));
    }

    @PostMapping("/auth/me/privacy-consent")
    public apiResponse<PrivacyConsentService.Status> consent(@Valid @RequestBody GateConsentRequest request, Authentication authentication) {
        service.recordSelf(authentication, PrivacyConsentSource.LOGIN_GATE);
        return apiResponse.success("Đã ghi nhận đồng ý", service.status(authentication));
    }

    @PostMapping("/auth/me/privacy-consent/withdraw")
    public apiResponse<PrivacyConsentService.Status> withdraw(Authentication authentication) {
        service.withdraw(authentication);
        return apiResponse.success("Đã rút đồng ý", service.status(authentication));
    }

    public static class GateConsentRequest extends SelfConsentRequest {
    }
}
