package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.training.TrainingDecisionNotifier;
import phucuong.ht.modules.training.courseInvitation.service.CourseInvitationAutoTrigger;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

class RegistrationDecisionNotifierTest {

    private final TrainingDecisionNotifier decisions = mock(TrainingDecisionNotifier.class);
    private final CourseInvitationAutoTrigger invitations = mock(CourseInvitationAutoTrigger.class);
    private final RegistrationDecisionNotifier notifier = RegistrationDecisionNotifier.noop();
    private final List<CourseParticipation> participations = List.of(new CourseParticipation());

    private void wire() {
        ReflectionTestUtils.setField(notifier, "decisionNotifier", decisions);
        ReflectionTestUtils.setField(notifier, "invitationAutoTrigger", invitations);
    }

    @Test
    void approvalNotifiesAndTriggersInvitationEmails() {
        wire();

        notifier.decided(participations, true, Map.of("1", "ghi chú"), "admin");

        verify(invitations).afterApproved(participations);
        verify(decisions).notifyRegistrations(Decision.REGISTRATION_APPROVED, participations, null, "admin");
    }

    @Test
    void rejectionPassesReasonsAndSkipsInvitationEmails() {
        wire();
        Map<String, String> reasons = Map.of("1", "thiếu giấy tờ");

        notifier.decided(participations, false, reasons, "admin");

        verify(invitations, never()).afterApproved(any());
        verify(decisions).notifyRegistrations(Decision.REGISTRATION_REJECTED, participations, reasons, "admin");
    }

    @Test
    void emptyDecisionSendsNothing() {
        wire();

        notifier.decided(List.of(), false, Map.of(), "admin");

        verifyNoInteractions(decisions);
    }

    @Test
    void removalNotifiesOnlyWhenConfigured() {
        notifier.removed(participations.get(0), "admin");
        wire();
        notifier.removed(participations.get(0), "admin");

        verify(decisions).notifyRegistrations(Decision.REGISTRATION_REMOVED, participations, null, "admin");
    }
}
