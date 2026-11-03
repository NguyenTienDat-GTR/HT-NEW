package phucuong.ht.modules.leader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.LeaderNames;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishPlace;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Báo cho các tài khoản có quyền xem hồ sơ huynh trưởng của giáo xứ nguồn và giáo xứ đích biết huynh trưởng nào vừa được chuyển và do ai chuyển.
 * Nếu hai giáo xứ khác giáo hạt thì báo thêm cho các tài khoản phạm vi giáo hạt của cả hai giáo hạt.
 * Người thực hiện không nhận thông báo của chính mình.
 */
@Component
@RequiredArgsConstructor
public class LeaderTransferNotifier {

    public static final String EVENT_TYPE = "LEADER_PARISH_TRANSFERRED";

    private final TrainingNotificationRecipientResolver recipientResolver;
    private final TrainingNotificationService trainingNotificationService;
    private final ParishRepository parishRepository;
    private final AuditActorNameResolver auditActorNameResolver;

    public void announce(Leader leader, Parish from, Parish to, String actor) {
        if (leader == null || from == null || to == null) {
            return;
        }
        Set<String> recipients = new LinkedHashSet<>();
        recipients.addAll(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_PARISH, UnitType.PARISH, from.getId()));
        recipients.addAll(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_PARISH, UnitType.PARISH, to.getId()));

        Optional<UUID> fromDeanery = parishRepository.findDeaneryIdByParishId(from.getId());
        Optional<UUID> toDeanery = parishRepository.findDeaneryIdByParishId(to.getId());
        if (fromDeanery.isPresent() && toDeanery.isPresent() && !fromDeanery.get().equals(toDeanery.get())) {
            recipients.addAll(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_DEANERY, UnitType.DEANERY, fromDeanery.get()));
            recipients.addAll(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_DEANERY, UnitType.DEANERY, toDeanery.get()));
        }
        recipients.remove(actor);
        if (recipients.isEmpty()) {
            return;
        }

        String actorName = auditActorNameResolver.resolve(actor);
        if (actorName == null || actorName.isBlank()) {
            actorName = actor;
        }
        trainingNotificationService.notifyUsers(List.copyOf(recipients), TrainingNotificationCommand.builder()
                .eventType(EVENT_TYPE)
                .title("Huynh trưởng được chuyển giáo xứ")
                .message(LeaderNames.displayName(leader) + " được chuyển từ " + describe(from) + " sang " + describe(to) + ", do " + actorName + " thực hiện")
                .senderUnitType(UnitType.PARISH)
                .senderUnitId(to.getId())
                .actor(actor)
                .build());
    }

    // Mỗi đơn vị kèm nhãn loại (Giáo xứ / Giáo họ / Giáo điểm), giáo hạt và giáo phận chứa nó; không tra được thì dùng nhãn Giáo xứ + tên.
    private String describe(Parish parish) {
        return parishRepository.findPlaceById(parish.getId())
                .map(ParishPlace::describe)
                .orElseGet(() -> "Giáo xứ " + parish.getName());
    }
}
