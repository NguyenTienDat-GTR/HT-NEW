package phucuong.ht.modules.mobile.push.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import phucuong.ht.modules.mobile.push.model.entity.PushDevice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    List<PushDevice> findByUsername(String username);

    Optional<PushDevice> findByUsernameAndDeviceId(String username, String deviceId);

    Optional<PushDevice> findByPushToken(String pushToken);

    long deleteByUsernameAndDeviceId(String username, String deviceId);

    long deleteByPushTokenIn(Collection<String> pushTokens);
}
