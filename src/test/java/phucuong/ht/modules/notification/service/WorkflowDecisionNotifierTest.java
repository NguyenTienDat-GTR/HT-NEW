package phucuong.ht.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDecisionNotifierTest {

    @Mock
    private TrainingNotificationService trainingNotificationService;
    @Mock
    private AuditActorNameResolver auditActorNameResolver;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private WorkflowDecisionNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new WorkflowDecisionNotifier(trainingNotificationService, auditActorNameResolver, new ObjectMapper());
        ReflectionTestUtils.setField(notifier, "entityManager", entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getResultList()).thenReturn(List.of("submitter"));
        lenient().when(auditActorNameResolver.resolve("admin")).thenReturn("Quản trị viên");
    }

    @Test
    void threeOrMoreEntriesOfSameCourseShouldBeGroupedIntoOneNotificationWithPayload() {
        notifier.notifyDecisions(Decision.REGISTRATION_APPROVED, entries(3), "admin");

        ArgumentCaptor<TrainingNotificationCommand> captor = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(trainingNotificationService, atLeastOnce()).notifyUsers(any(), captor.capture());
        List<TrainingNotificationCommand> sent = captor.getAllValues();
        assertEquals(1, sent.size());
        TrainingNotificationCommand command = sent.get(0);
        assertEquals("3 hồ sơ đăng ký được duyệt", command.title());
        assertNotNull(command.payload());
        assertTrue(command.payload().contains("DECISION_LIST"));
        assertTrue(command.message().contains("Khóa Huấn luyện A") || command.message().contains("khóa Huấn luyện A"));
    }

    @Test
    void fewerThanThreeEntriesShouldSendOneNotificationEach() {
        notifier.notifyDecisions(Decision.REGISTRATION_REJECTED, entries(2), "admin");

        ArgumentCaptor<TrainingNotificationCommand> captor = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(trainingNotificationService, atLeastOnce()).notifyUsers(any(), captor.capture());
        List<TrainingNotificationCommand> sent = captor.getAllValues();
        assertEquals(2, sent.size());
        assertTrue(sent.get(0).message().contains("không được duyệt"));
        assertTrue(sent.get(0).message().contains("Lý do: thiếu giấy tờ"));
        // Thông báo lẻ cũng mở bảng chi tiết như thông báo phân quyền: khóa, huynh trưởng, lý do và người thực hiện.
        assertNotNull(sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("DECISION_LIST"), sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("Huấn luyện A"), sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("Huynh trưởng 1"), sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("thiếu giấy tờ"), sent.get(0).payload());
        assertTrue(sent.get(0).payload().contains("Quản trị viên"), sent.get(0).payload());
        assertTrue(sent.get(0).message().contains("Chọn thông báo để xem chi tiết"));
    }

    @Test
    void theLeaderWithAnAccountAlsoReceivesTheNotificationAboutTheirOwnRecord() {
        UUID leaderId = UUID.randomUUID();
        Query leaderQuery = org.mockito.Mockito.mock(Query.class);
        lenient().when(leaderQuery.setParameter(anyString(), any())).thenReturn(leaderQuery);
        lenient().when(leaderQuery.getResultList()).thenReturn(List.of("tai-khoan-huynh-truong"));
        lenient().when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.contains("a.leader_id"))).thenReturn(leaderQuery);

        notifier.notifyDecisions(Decision.CERTIFICATE_REVOKED,
                List.of(new Entry("submitter", UUID.randomUUID(), "Giáo xứ Phú Cường", "KH-A", "Huấn luyện A", "Nguyễn Văn An", "GPPC-HTI-2026-1",
                        "nhập nhầm", leaderId)), "admin");

        ArgumentCaptor<List<String>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(trainingNotificationService, atLeastOnce()).notifyUsers(recipients.capture(), command.capture());
        assertTrue(recipients.getValue().contains("tai-khoan-huynh-truong"), recipients.getValue().toString());
        assertTrue(recipients.getValue().contains("submitter"), recipients.getValue().toString());
        assertEquals("training.certificate.revoked", command.getValue().eventType());
        assertTrue(command.getValue().payload().contains("GPPC-HTI-2026-1"), command.getValue().payload());
    }

    private List<Entry> entries(int count) {
        List<Entry> entries = new ArrayList<>();
        UUID parishId = UUID.randomUUID();
        for (int index = 1; index <= count; index++) {
            entries.add(new Entry("submitter", parishId, "Giáo xứ Phú Cường", "KH-A", "Huấn luyện A",
                    "Huynh trưởng " + index, null, "thiếu giấy tờ"));
        }
        return entries;
    }
}
