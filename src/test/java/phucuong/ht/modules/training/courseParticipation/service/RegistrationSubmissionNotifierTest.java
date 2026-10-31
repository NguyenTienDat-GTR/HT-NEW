package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationSubmissionNotifierTest {

    private final TrainingNotificationRecipientResolver resolver = mock(TrainingNotificationRecipientResolver.class);
    private final TrainingNotificationService notificationService = mock(TrainingNotificationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final RegistrationSubmissionNotifier notifier =
            new RegistrationSubmissionNotifier(resolver, notificationService, eventPublisher, new TrainingWorkflowSupport());

    @Test
    void announcesToDioceseApproversAndPublishesBatchEvent() {
        UUID dioceseId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("K1");
        course.setCourseName("Khóa 1");
        course.setDiocese(diocese);
        when(resolver.resolveRecipients(null, CourseParticipationAccessEvaluator.APPROVE_DIOCESE, UnitType.DIOCESE, dioceseId))
                .thenReturn(List.of("approver"));

        notifier.announce(course, List.of(new CourseParticipation(), new CourseParticipation()), UnitType.PARISH, senderId, "Giáo xứ A", "actor");

        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notificationService).notifyUsers(eq(List.of("approver")), command.capture());
        assertEquals("Giáo xứ A gửi 2 đăng ký cho khóa Khóa 1", command.getValue().message());

        ArgumentCaptor<DomainEventMessage> event = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(eventPublisher).publishAfterCommit(event.capture());
        assertEquals("training.registration.batch_submitted", event.getValue().getEventType());
        assertEquals("K1", event.getValue().getEntityId());
        assertEquals(2, event.getValue().getPayload().get("totalCount"));
    }
}
