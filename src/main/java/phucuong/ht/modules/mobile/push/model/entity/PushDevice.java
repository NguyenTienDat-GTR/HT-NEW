package phucuong.ht.modules.mobile.push.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Một thiết bị của một tài khoản đã đăng ký nhận thông báo đẩy qua Expo Push Service. */
@Entity
@Table(name = "push_device")
@Getter
@Setter
@NoArgsConstructor
public class PushDevice {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false)
    private String username;

    /** Mã thiết bị do app sinh và lưu cục bộ (cùng mã gửi lúc đăng nhập). */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /** Mã đẩy Expo, dạng ExponentPushToken[...]. */
    @Column(name = "push_token", nullable = false)
    private String pushToken;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
