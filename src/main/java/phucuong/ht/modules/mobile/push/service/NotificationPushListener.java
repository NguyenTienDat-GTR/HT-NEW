package phucuong.ht.modules.mobile.push.service;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;

/**
 * Bắt sự kiện "thông báo mới đã được lưu" ở tầng Hibernate để đẩy thông báo cho app di động, mà KHÔNG sửa
 * {@code TrainingNotificationService} hay luồng WebSocket của web. Chỉ chạy SAU KHI giao dịch tạo thông báo đã commit
 * (giao dịch bị hủy thì không đẩy gì), rồi giao việc cho {@link MobilePushDispatcher} xử lý ở hàng đợi riêng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener implements PostCommitInsertEventListener {

    private final EntityManagerFactory entityManagerFactory;
    private final MobilePushDispatcher dispatcher;

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .requireService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.POST_COMMIT_INSERT).appendListener(this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (!(event.getEntity() instanceof TrainingNotification notification)) {
            return;
        }
        try {
            dispatcher.dispatch(new MobilePushDispatcher.PushNotification(
                    notification.getUsername(),
                    notification.getId(),
                    notification.getEventType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getCourseCode(),
                    notification.getSenderUnitId() == null ? null : notification.getSenderUnitId().toString()));
        } catch (RuntimeException exception) {
            log.warn("Không chuyển được thông báo {} sang hàng đợi đẩy: {}", notification.getId(), exception.toString());
        }
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
        // Giao dịch lỗi hoặc bị hủy: thông báo không tồn tại nên không đẩy gì.
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return TrainingNotification.class.isAssignableFrom(persister.getMappedClass());
    }
}
