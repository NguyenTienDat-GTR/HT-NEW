package phucuong.ht.modules.mobile.push.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Đăng ký thiết bị nhận thông báo đẩy. */
@Getter
@Setter
public class MobilePushDeviceRequest {

    @NotBlank(message = "Mã thiết bị là bắt buộc")
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "Mã thiết bị không hợp lệ")
    private String deviceId;

    @NotBlank(message = "Mã đẩy là bắt buộc")
    @Size(max = 255, message = "Mã đẩy không hợp lệ")
    @Pattern(regexp = "^Expo(nent)?PushToken\\[[^\\]\\s]+\\]$", message = "Mã đẩy không hợp lệ")
    private String pushToken;

    @NotBlank(message = "Nền tảng là bắt buộc")
    @Pattern(regexp = "^(?i)(ios|android)$", message = "Nền tảng không hợp lệ")
    private String platform;

    @Size(max = 32, message = "Phiên bản app không hợp lệ")
    private String appVersion;
}
