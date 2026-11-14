package phucuong.ht.modules.mobile.push.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.mobile.push.model.entity.PushDevice;
import phucuong.ht.modules.mobile.push.model.request.MobilePushDeviceRequest;
import phucuong.ht.modules.mobile.push.repository.PushDeviceRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Đăng ký, hủy và tra cứu thiết bị nhận thông báo đẩy của app di động. */
@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final PushDeviceRepository repository;

    /**
     * Đăng ký (hoặc làm mới) thiết bị của tài khoản. Một mã đẩy chỉ thuộc một bản ghi: nếu mã đang gắn với tài khoản hay
     * thiết bị khác (máy đổi tài khoản, cài lại app) thì bản ghi cũ bị thay để thông báo không đi nhầm người.
     */
    @Transactional
    public void register(String username, MobilePushDeviceRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        PushDevice target = repository.findByUsernameAndDeviceId(username, request.getDeviceId()).orElse(null);
        repository.findByPushToken(request.getPushToken()).ifPresent(existing -> {
            if (target == null || !existing.getId().equals(target.getId())) {
                repository.delete(existing);
                repository.flush();
            }
        });

        PushDevice device = target;
        if (device == null) {
            device = new PushDevice();
            device.setId(UUID.randomUUID());
            device.setUsername(username);
            device.setDeviceId(request.getDeviceId());
            device.setCreatedAt(now);
        }
        device.setPushToken(request.getPushToken());
        device.setPlatform(request.getPlatform().toLowerCase(Locale.ROOT));
        device.setAppVersion(request.getAppVersion());
        device.setLastSeenAt(now);
        repository.save(device);
    }

    /** App gọi khi đăng xuất; không có bản ghi thì bỏ qua. */
    @Transactional
    public void unregister(String username, String deviceId) {
        repository.deleteByUsernameAndDeviceId(username, deviceId);
    }

    /** Xóa các mã Expo báo không còn hợp lệ (DeviceNotRegistered). */
    @Transactional
    public void removeTokens(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        repository.deleteByPushTokenIn(tokens);
    }

    @Transactional(readOnly = true)
    public List<PushDevice> devicesOf(String username) {
        return repository.findByUsername(username);
    }
}
