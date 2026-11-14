package phucuong.ht.modules.mobile.push.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Bắt sự kiện ở tầng Hibernate: thông báo chỉ được đẩy sau khi giao dịch tạo nó commit, giao dịch bị hủy thì không đẩy.
 * Dùng bản giả của hàng đợi nên không gọi Expo thật.
 */
@SpringBootTest(properties = "app.rate-limit.enabled=false")
class NotificationPushListenerIntegrationTest {

    @Autowired
    private TrainingNotificationRepository notifications;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private MobilePushDispatcher dispatcher;

    private static final String USER = "push.listener.user";

    @BeforeEach
    void createAccount() {
        // training_notification.username có khóa ngoại tới account.
        Account account = new Account();
        account.setUsername(USER);
        account.setPassword("x");
        account.setCreatedAt(OffsetDateTime.now());
        account.setStatus(Boolean.TRUE);
        accounts.saveAndFlush(account);
    }

    @AfterEach
    void deleteAccount() {
        accounts.deleteById(USER);
    }

    private TrainingNotification notification() {
        OffsetDateTime now = OffsetDateTime.now();
        TrainingNotification notification = new TrainingNotification();
        notification.setUsername(USER);
        notification.setEventType("PUSH_LISTENER_TEST");
        notification.setTitle("Tiêu đề thử");
        notification.setMessage("Nội dung thử");
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        notification.setStatus(Boolean.TRUE);
        return notification;
    }

    @Test
    void pushesOnlyAfterTheTransactionCommits() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String id = template.execute(status -> {
            String savedId = notifications.save(notification()).getId();
            notifications.flush();
            // Chưa commit: chưa được đẩy.
            verify(dispatcher, never()).dispatch(any());
            return savedId;
        });
        try {
            var captor = forClass(MobilePushDispatcher.PushNotification.class);
            verify(dispatcher).dispatch(captor.capture());
            assertEquals("push.listener.user", captor.getValue().username());
            assertEquals(id, captor.getValue().notificationId());
            assertEquals("PUSH_LISTENER_TEST", captor.getValue().eventType());
            assertEquals("Tiêu đề thử", captor.getValue().title());
        } finally {
            notifications.deleteById(id);
        }
    }

    @Test
    void rolledBackTransactionPushesNothing() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            notifications.save(notification());
            notifications.flush();
            status.setRollbackOnly();
        });

        verify(dispatcher, never()).dispatch(any());
    }
}
