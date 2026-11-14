package phucuong.ht.modules.mobile.push.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import phucuong.ht.modules.mobile.push.model.entity.PushDevice;
import phucuong.ht.modules.mobile.push.model.request.MobilePushDeviceRequest;
import phucuong.ht.modules.mobile.push.repository.PushDeviceRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.rate-limit.enabled=false")
class PushDeviceServiceIntegrationTest {

    private static final String A = "push.svc.a";
    private static final String B = "push.svc.b";

    @Autowired
    private PushDeviceService service;
    @Autowired
    private PushDeviceRepository repository;

    @AfterEach
    void cleanUp() {
        for (String user : List.of(A, B)) {
            repository.findByUsername(user).forEach(repository::delete);
        }
    }

    private MobilePushDeviceRequest request(String deviceId, String token) {
        MobilePushDeviceRequest request = new MobilePushDeviceRequest();
        request.setDeviceId(deviceId);
        request.setPushToken(token);
        request.setPlatform("ios");
        request.setAppVersion("1.0.0");
        return request;
    }

    @Test
    void sameDeviceKeepsOneRowAndUpdatesTheToken() {
        service.register(A, request("d1", "ExponentPushToken[svc-1]"));
        service.register(A, request("d1", "ExponentPushToken[svc-2]"));

        List<PushDevice> devices = service.devicesOf(A);
        assertEquals(1, devices.size());
        assertEquals("ExponentPushToken[svc-2]", devices.get(0).getPushToken());
    }

    @Test
    void aTokenMovesToTheNewAccountSoNotificationsNeverGoToThePreviousOne() {
        service.register(A, request("d1", "ExponentPushToken[svc-shared]"));
        service.register(B, request("d9", "ExponentPushToken[svc-shared]"));

        assertTrue(service.devicesOf(A).isEmpty());
        assertEquals(1, service.devicesOf(B).size());
    }

    @Test
    void severalDevicesPerAccountAndRemoveStaleTokens() {
        service.register(A, request("d1", "ExponentPushToken[svc-a1]"));
        service.register(A, request("d2", "ExponentPushToken[svc-a2]"));
        assertEquals(2, service.devicesOf(A).size());

        service.removeTokens(List.of("ExponentPushToken[svc-a1]"));
        assertEquals(1, service.devicesOf(A).size());

        service.unregister(A, "d2");
        assertTrue(service.devicesOf(A).isEmpty());
        service.unregister(A, "khong-co");
    }
}
