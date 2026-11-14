package phucuong.ht.modules.mobile.push.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.mobile.push.model.entity.PushDevice;
import phucuong.ht.modules.mobile.push.sender.PushSender;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Gửi thông báo đẩy cho các thiết bị của người nhận, chạy ở hàng đợi riêng sau khi giao dịch tạo thông báo đã commit.
 * Lỗi hoặc chậm ở Expo không bao giờ quay ngược lại ảnh hưởng luồng tạo thông báo và WebSocket của web.
 *
 * <p>Hàng đợi là {@link ExecutorService} nội bộ, cố ý KHÔNG khai báo thành bean {@code Executor} để không đổi
 * {@code applicationTaskExecutor} mặc định của Spring mà phần web đang dùng.
 */
@Slf4j
@Component
public class MobilePushDispatcher {

    static final int MAX_BODY_LENGTH = 160;
    static final String GENERIC_BODY = "Bạn có thông báo mới.";
    static final String ANDROID_CHANNEL_ID = "default";

    private final PushDeviceService devices;
    private final AccountRepository accounts;
    private final PushSender sender;
    private final boolean enabled;
    private final boolean includeMessage;
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
            runnable -> {
                Thread thread = new Thread(runnable, "mobile-push");
                thread.setDaemon(true);
                return thread;
            });

    public MobilePushDispatcher(
            PushDeviceService devices,
            AccountRepository accounts,
            PushSender sender,
            @Value("${app.mobile.push.enabled:true}") boolean enabled,
            @Value("${app.mobile.push.include-message:true}") boolean includeMessage
    ) {
        this.devices = devices;
        this.accounts = accounts;
        this.sender = sender;
        this.enabled = enabled;
        this.includeMessage = includeMessage;
    }

    /** Nội dung cần đẩy cho một thông báo vừa được lưu. */
    public record PushNotification(
            String username, String notificationId, String eventType, String title, String message,
            String courseCode, String senderUnitId
    ) {
    }

    /** Xếp vào hàng đợi rồi trả về ngay; không bao giờ ném lỗi cho người gọi. */
    public void dispatch(PushNotification notification) {
        if (!enabled || notification == null || notification.username() == null) {
            return;
        }
        try {
            executor.execute(() -> deliver(notification));
        } catch (RejectedExecutionException exception) {
            log.warn("Hàng đợi thông báo đẩy đầy, bỏ qua thông báo {}", notification.notificationId());
        } catch (RuntimeException exception) {
            log.warn("Không xếp được thông báo đẩy {}: {}", notification.notificationId(), exception.toString());
        }
    }

    /** Chạy đồng bộ (cho test). Bỏ qua tài khoản đã bị tắt; xóa mã thiết bị Expo báo không còn hợp lệ. */
    void deliver(PushNotification notification) {
        try {
            boolean active = accounts.findById(notification.username())
                    .map(account -> Boolean.TRUE.equals(account.getStatus()))
                    .orElse(false);
            if (!active) {
                return;
            }
            List<PushDevice> targets = devices.devicesOf(notification.username());
            if (targets.isEmpty()) {
                return;
            }
            List<PushSender.Message> messages = new ArrayList<>(targets.size());
            for (PushDevice device : targets) {
                messages.add(new PushSender.Message(
                        device.getPushToken(),
                        notification.title(),
                        bodyOf(notification),
                        dataOf(notification),
                        "android".equals(device.getPlatform()) ? ANDROID_CHANNEL_ID : null));
            }
            List<String> stale = new ArrayList<>();
            for (PushSender.Result result : sender.send(messages)) {
                if (result.deviceNotRegistered()) {
                    stale.add(result.to());
                }
            }
            devices.removeTokens(stale);
        } catch (RuntimeException exception) {
            log.warn("Gửi thông báo đẩy {} thất bại: {}", notification.notificationId(), exception.toString());
        }
    }

    private String bodyOf(PushNotification notification) {
        if (!includeMessage || notification.message() == null || notification.message().isBlank()) {
            return GENERIC_BODY;
        }
        String message = notification.message().strip();
        return message.length() <= MAX_BODY_LENGTH ? message : message.substring(0, MAX_BODY_LENGTH - 1) + "…";
    }

    /** Dữ liệu để app điều hướng khi người dùng bấm thông báo. */
    private Map<String, Object> dataOf(PushNotification notification) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificationId", notification.notificationId());
        data.put("eventType", notification.eventType());
        if (notification.courseCode() != null) {
            data.put("courseCode", notification.courseCode());
        }
        if (notification.senderUnitId() != null) {
            data.put("senderUnitId", notification.senderUnitId());
        }
        return data;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
