package phucuong.ht.modules.leader.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishPlace;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderTransferNotifierTest {

    private final TrainingNotificationRecipientResolver recipientResolver = mock(TrainingNotificationRecipientResolver.class);
    private final TrainingNotificationService notificationService = mock(TrainingNotificationService.class);
    private final ParishRepository parishRepository = mock(ParishRepository.class);
    private final AuditActorNameResolver actorNameResolver = mock(AuditActorNameResolver.class);
    private final LeaderTransferNotifier notifier = new LeaderTransferNotifier(recipientResolver, notificationService, parishRepository, actorNameResolver);

    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();
    private final Parish from = parish(fromId, "Giáo xứ A");
    private final Parish to = parish(toId, "Giáo xứ B");
    private final Leader leader = leader();

    @Test
    void sameDeaneryNotifiesParishAccountsOnly() {
        UUID deaneryId = UUID.randomUUID();
        when(parishRepository.findDeaneryIdByParishId(fromId)).thenReturn(Optional.of(deaneryId));
        when(parishRepository.findDeaneryIdByParishId(toId)).thenReturn(Optional.of(deaneryId));
        when(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_PARISH, UnitType.PARISH, fromId)).thenReturn(List.of("parish-a", "mover"));
        when(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_PARISH, UnitType.PARISH, toId)).thenReturn(List.of("parish-b"));
        when(actorNameResolver.resolve("mover")).thenReturn("Nguyễn Văn Mover");
        when(parishRepository.findPlaceById(fromId)).thenReturn(Optional.of(new ParishPlace("Giáo xứ", "A", "Thủ Dầu Một", "Phú Cường")));
        when(parishRepository.findPlaceById(toId)).thenReturn(Optional.of(new ParishPlace("Giáo họ", "B", "Thủ Dầu Một", "Phú Cường")));

        notifier.announce(leader, from, to, "mover");

        ArgumentCaptor<List<String>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notificationService).notifyUsers(recipients.capture(), command.capture());
        assertEquals(List.of("parish-a", "parish-b"), recipients.getValue());
        assertEquals(LeaderTransferNotifier.EVENT_TYPE, command.getValue().eventType());
        assertEquals("Giuse Nguyễn Văn Hùng được chuyển từ Giáo xứ A (Giáo hạt Thủ Dầu Một, Giáo phận Phú Cường) sang Giáo họ B (Giáo hạt Thủ Dầu Một, Giáo phận Phú Cường), do Nguyễn Văn Mover thực hiện",
                command.getValue().message());
        verify(recipientResolver, never()).resolveRecipients(any(), eq(LeaderAccessEvaluator.READ_DEANERY), eq(UnitType.DEANERY), any());
    }

    @Test
    void differentDeaneryAlsoNotifiesDeaneryAccountsOfBoth() {
        UUID fromDeanery = UUID.randomUUID();
        UUID toDeanery = UUID.randomUUID();
        when(parishRepository.findDeaneryIdByParishId(fromId)).thenReturn(Optional.of(fromDeanery));
        when(parishRepository.findDeaneryIdByParishId(toId)).thenReturn(Optional.of(toDeanery));
        when(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_PARISH, UnitType.PARISH, fromId)).thenReturn(List.of("parish-a"));
        when(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_PARISH, UnitType.PARISH, toId)).thenReturn(List.of("parish-b"));
        when(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_DEANERY, UnitType.DEANERY, fromDeanery)).thenReturn(List.of("deanery-1"));
        when(recipientResolver.resolveRecipients(null, LeaderAccessEvaluator.READ_DEANERY, UnitType.DEANERY, toDeanery)).thenReturn(List.of("deanery-2"));
        when(actorNameResolver.resolve("mover")).thenReturn(null);

        notifier.announce(leader, from, to, "mover");

        ArgumentCaptor<List<String>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notificationService).notifyUsers(recipients.capture(), command.capture());
        assertEquals(List.of("parish-a", "parish-b", "deanery-1", "deanery-2"), recipients.getValue());
        assertTrue(command.getValue().message().contains("do mover thực hiện"));
    }

    @Test
    void noRecipientsSendsNothing() {
        when(parishRepository.findDeaneryIdByParishId(any())).thenReturn(Optional.empty());
        when(recipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of());

        notifier.announce(leader, from, to, "mover");

        verify(notificationService, never()).notifyUsers(any(), any());
    }

    private static Parish parish(UUID id, String name) {
        Parish parish = new Parish();
        parish.setId(id);
        parish.setName(name);
        return parish;
    }

    private static Leader leader() {
        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setFirstName("Hùng");
        leader.setLastName("Nguyễn Văn");
        leader.setHolyName("Giuse");
        return leader;
    }
}
