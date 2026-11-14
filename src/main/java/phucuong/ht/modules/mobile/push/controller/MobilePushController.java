package phucuong.ht.modules.mobile.push.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.mobile.push.model.request.MobilePushDeviceRequest;
import phucuong.ht.modules.mobile.push.service.PushDeviceService;

/**
 * Đăng ký thiết bị nhận thông báo đẩy của app di động. Cần đăng nhập; chỉ thao tác trên thiết bị của chính tài khoản.
 * Chỉ thêm mới: không đổi luồng tạo thông báo hay WebSocket mà web đang dùng.
 */
@RestController
@RequestMapping("/api/mobile/push")
@RequiredArgsConstructor
public class MobilePushController {

    private final PushDeviceService pushDeviceService;
    private final securityUtils securityUtils;

    @PostMapping("/devices")
    public apiResponse<Void> register(@Valid @RequestBody MobilePushDeviceRequest request, Authentication authentication) {
        pushDeviceService.register(securityUtils.currentUsername(authentication), request);
        return apiResponse.success("Đã đăng ký thiết bị nhận thông báo", null);
    }

    @DeleteMapping("/devices/{deviceId}")
    public apiResponse<Void> unregister(@PathVariable String deviceId, Authentication authentication) {
        pushDeviceService.unregister(securityUtils.currentUsername(authentication), deviceId);
        return apiResponse.success("Đã hủy đăng ký thiết bị", null);
    }
}
