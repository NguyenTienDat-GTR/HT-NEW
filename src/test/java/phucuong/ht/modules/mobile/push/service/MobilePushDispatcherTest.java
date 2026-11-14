package phucuong.ht.modules.mobile.push.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.mobile.push.model.entity.PushDevice;
import phucuong.ht.modules.mobile.push.sender.PushSender;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MobilePushDispatcherTest {

    @Mock
    private PushDeviceService devices;
    @Mock
    private AccountRepository accounts;
    @Mock
    private PushSender sender;

    private MobilePushDispatcher dispatcher;

    private MobilePushDispatcher.PushNotification notification(String message) {
        return new MobilePushDispatcher.PushNotification("ht.an", "n1", "CERTIFICATE_APPROVED", "Chứng nhận", message, "K1", null);
    }

    private PushDevice device(String token, String platform) {
        PushDevice device = new PushDevice();
        device.setPushToken(token);
        device.setPlatform(platform);
        return device;
    }

    private Account activeAccount(boolean status) {
        Account account = new Account();
        account.setUsername("ht.an");
        account.setStatus(status);
        return account;
    }

    @BeforeEach
    void setUp() {
        dispatcher = new MobilePushDispatcher(devices, accounts, sender, true, true);
        when(accounts.findById("ht.an")).thenReturn(Optional.of(activeAccount(true)));
        when(sender.send(anyList())).thenReturn(List.of());
    }

    @Test
    void sendsToEveryDeviceWithNavigationData() {
        when(devices.devicesOf("ht.an")).thenReturn(List.of(device("ExponentPushToken[a]", "android"), device("ExponentPushToken[i]", "ios")));

        dispatcher.deliver(notification("Chứng nhận của bạn đã được duyệt."));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushSender.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(sender).send(captor.capture());
        List<PushSender.Message> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertEquals("Chứng nhận của bạn đã được duyệt.", sent.get(0).body());
        assertEquals("n1", sent.get(0).data().get("notificationId"));
        assertEquals("CERTIFICATE_APPROVED", sent.get(0).data().get("eventType"));
        assertEquals("K1", sent.get(0).data().get("courseCode"));
        assertFalse(sent.get(0).data().containsKey("senderUnitId"));
        assertEquals("default", sent.get(0).channelId());
        assertNull(sent.get(1).channelId());
    }

    @Test
    void removesTokensExpoReportsAsNotRegistered() {
        when(devices.devicesOf("ht.an")).thenReturn(List.of(device("ExponentPushToken[a]", "android"), device("ExponentPushToken[b]", "ios")));
        when(sender.send(anyList())).thenReturn(List.of(
                new PushSender.Result("ExponentPushToken[a]", true, null),
                new PushSender.Result("ExponentPushToken[b]", false, "DeviceNotRegistered")));

        dispatcher.deliver(notification("x"));

        verify(devices).removeTokens(List.of("ExponentPushToken[b]"));
    }

    @Test
    void skipsDisabledAndUnknownAccounts() {
        when(accounts.findById("ht.an")).thenReturn(Optional.of(activeAccount(false)));
        dispatcher.deliver(notification("x"));
        when(accounts.findById("ht.an")).thenReturn(Optional.empty());
        dispatcher.deliver(notification("x"));

        verify(sender, never()).send(anyList());
        verify(devices, never()).devicesOf(anyString());
    }

    @Test
    void doesNothingWithoutDevices() {
        when(devices.devicesOf("ht.an")).thenReturn(List.of());

        dispatcher.deliver(notification("x"));

        verify(sender, never()).send(anyList());
    }

    @Test
    void truncatesLongBodiesAndCanHideTheMessage() {
        when(devices.devicesOf("ht.an")).thenReturn(List.of(device("ExponentPushToken[a]", "ios")));
        dispatcher.deliver(notification("a".repeat(300)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushSender.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(sender).send(captor.capture());
        assertEquals(MobilePushDispatcher.MAX_BODY_LENGTH, captor.getValue().get(0).body().length());

        MobilePushDispatcher hidden = new MobilePushDispatcher(devices, accounts, sender, true, false);
        hidden.deliver(notification("Nội dung riêng tư"));
        verify(sender, org.mockito.Mockito.times(2)).send(captor.capture());
        assertEquals(MobilePushDispatcher.GENERIC_BODY, captor.getValue().get(0).body());
    }

    @Test
    void neverPropagatesFailures() {
        when(devices.devicesOf("ht.an")).thenThrow(new IllegalStateException("db down"));

        dispatcher.deliver(notification("x"));
        dispatcher.dispatch(notification("x"));
        dispatcher.dispatch(null);
    }

    @Test
    void disabledFlagDoesNotQueueAnything() throws Exception {
        MobilePushDispatcher off = new MobilePushDispatcher(devices, accounts, sender, false, true);

        off.dispatch(notification("x"));
        Thread.sleep(100);

        verify(accounts, never()).findById(any());
        verify(sender, never()).send(anyList());
    }
}
