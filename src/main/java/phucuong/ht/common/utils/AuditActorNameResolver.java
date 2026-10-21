package phucuong.ht.common.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;

import java.util.UUID;

/**
 * createdBy/updatedBy lưu mã huynh trưởng hoặc "super-admin"; chuyển thành tên hiển thị cho màn hình chi tiết.
 */
@Component
@RequiredArgsConstructor
public class AuditActorNameResolver {

    private static final String SUPER_ADMIN_ACTOR = "super-admin";

    private final LeaderRepository leaderRepository;

    public String resolve(String actor) {
        if (actor == null || actor.isBlank()) {
            return null;
        }
        if (SUPER_ADMIN_ACTOR.equals(actor)) {
            return "Quản trị tối cao";
        }
        try {
            return leaderRepository.findById(UUID.fromString(actor))
                    .map(this::displayName)
                    .orElse(actor);
        } catch (IllegalArgumentException ex) {
            return actor;
        }
    }

    private String displayName(Leader leader) {
        String name = leader.getLastName() == null || leader.getLastName().isBlank()
                ? leader.getFirstName()
                : leader.getLastName() + " " + leader.getFirstName();
        return leader.getHolyName() == null || leader.getHolyName().isBlank() ? name : leader.getHolyName() + " " + name;
    }
}
