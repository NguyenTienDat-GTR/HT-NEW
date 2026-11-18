package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Alert;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class MonitoringAlertNotifierTest {

    private static final Alert LOGIN = new Alert("LOGIN_ATTACK", "CRITICAL", "Nghi dò mật khẩu từ 203.0.113.5", "37 lần đăng nhập thất bại trong 60 phút qua.");
    private static final Alert SPIKE = new Alert("TRAFFIC_SPIKE", "WARNING", "Lưu lượng tăng đột biến", "640 request trong 60 phút qua.");
    private static final Alert INFO = new Alert("IP_UNRELIABLE", "INFO", "Chưa nhận được IP thật của người dùng", "Phần lớn là địa chỉ nội bộ.");

    private SystemMonitoringService monitoring;
    private SuperAdminEmailResolver recipients;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private JavaMailSender mailSender;
    private MonitoringAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        monitoring = mock(SystemMonitoringService.class);
        recipients = mock(SuperAdminEmailResolver.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        mailSender = mock(JavaMailSender.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(recipients.emails()).thenReturn(List.of("admin@example.com"));
        notifier = new MonitoringAlertNotifier(monitoring, recipients, redis, mailSender, 360, "noreply@example.com", "https://clead.io.vn/");
    }

    @Test
    void doesNothingWhenOnlyInformationalAlertsExist() {
        when(monitoring.recentAlerts()).thenReturn(List.of(INFO));

        assertEquals(0, notifier.notifyNewAlerts());

        verifyNoInteractions(recipients, mailSender);
    }

    @Test
    void doesNotClaimAlertsWhenNoSuperAdminHasAnEmail() {
        when(monitoring.recentAlerts()).thenReturn(List.of(LOGIN));
        when(recipients.emails()).thenReturn(List.of());

        assertEquals(0, notifier.notifyNewAlerts());

        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(mailSender);
    }

    @Test
    void sendsOneMailPerRecipientWithEveryNewAlert() {
        when(monitoring.recentAlerts()).thenReturn(List.of(LOGIN, INFO, SPIKE));
        when(recipients.emails()).thenReturn(List.of("a@example.com", "b@example.com"));

        assertEquals(2, notifier.notifyNewAlerts());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(sent.capture());
        SimpleMailMessage first = sent.getAllValues().get(0);
        assertArrayEquals(new String[]{"a@example.com"}, first.getTo());
        assertArrayEquals(new String[]{"b@example.com"}, sent.getAllValues().get(1).getTo());
        assertEquals("noreply@example.com", first.getFrom());
        assertTrue(first.getSubject().startsWith("[Nghiêm trọng]"), first.getSubject());
        assertTrue(first.getSubject().contains("2 cảnh báo mới"), first.getSubject());
        assertTrue(first.getText().contains("Nghi dò mật khẩu từ 203.0.113.5"));
        assertTrue(first.getText().contains("Lưu lượng tăng đột biến"));
        assertTrue(!first.getText().contains("Chưa nhận được IP thật"), "Cảnh báo thông tin không được gửi");
        assertTrue(first.getText().contains("https://clead.io.vn/system/monitoring"));
        assertTrue(first.getText().contains("6 giờ"));
        verify(values).setIfAbsent(eq(MonitoringAlertNotifier.key(LOGIN)), anyString(), eq(Duration.ofMinutes(360)));
    }

    @Test
    void warningOnlyMailIsMarkedAsNeedingAttention() {
        when(monitoring.recentAlerts()).thenReturn(List.of(SPIKE));

        notifier.notifyNewAlerts();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertTrue(sent.getValue().getSubject().startsWith("[Cần chú ý]"));
    }

    @Test
    void skipsAlertsAlreadySentRecently() {
        when(monitoring.recentAlerts()).thenReturn(List.of(LOGIN, SPIKE));
        when(values.setIfAbsent(eq(MonitoringAlertNotifier.key(LOGIN)), anyString(), any(Duration.class))).thenReturn(false);

        assertEquals(1, notifier.notifyNewAlerts());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertTrue(!sent.getValue().getText().contains("Nghi dò mật khẩu"));
        assertTrue(sent.getValue().getText().contains("Lưu lượng tăng đột biến"));
    }

    @Test
    void sendsNothingWhenEveryAlertWasAlreadySent() {
        when(monitoring.recentAlerts()).thenReturn(List.of(LOGIN));
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertEquals(0, notifier.notifyNewAlerts());

        verifyNoInteractions(mailSender);
    }

    @Test
    void releasesAlertsSoTheyAreRetriedWhenNobodyReceivedTheMail() {
        when(monitoring.recentAlerts()).thenReturn(List.of(LOGIN, SPIKE));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertEquals(0, notifier.notifyNewAlerts());

        ArgumentCaptor<Collection<String>> released = ArgumentCaptor.forClass(Collection.class);
        verify(redis).delete(released.capture());
        assertEquals(List.of(MonitoringAlertNotifier.key(LOGIN), MonitoringAlertNotifier.key(SPIKE)), List.copyOf(released.getValue()));
    }

    @Test
    void keepsAlertsMarkedWhenAtLeastOneRecipientReceivedTheMail() {
        when(monitoring.recentAlerts()).thenReturn(List.of(LOGIN));
        when(recipients.emails()).thenReturn(List.of("bad@example.com", "good@example.com"));
        doThrow(new MailSendException("rejected")).when(mailSender).send(org.mockito.ArgumentMatchers.argThat((SimpleMailMessage m) -> m.getTo()[0].equals("bad@example.com")));

        assertEquals(1, notifier.notifyNewAlerts());

        verify(redis, never()).delete(any(Collection.class));
    }

    @Test
    void swallowsUnexpectedErrorsSoTheScheduleKeepsRunning() {
        when(monitoring.recentAlerts()).thenThrow(new IllegalStateException("database down"));

        assertEquals(0, notifier.notifyNewAlerts());
    }

    @Test
    void escalationToHigherSeverityIsANewAlert() {
        Alert warning = new Alert("LOGIN_ATTACK", "WARNING", LOGIN.title(), "12 lần");

        assertNotEquals(MonitoringAlertNotifier.key(warning), MonitoringAlertNotifier.key(LOGIN));
    }
}
