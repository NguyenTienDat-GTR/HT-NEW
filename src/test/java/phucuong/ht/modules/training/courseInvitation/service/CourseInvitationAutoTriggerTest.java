package phucuong.ht.modules.training.courseInvitation.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CourseInvitationAutoTriggerTest {

    private CourseInvitationAsyncRunner runner;
    private CourseInvitationAutoTrigger trigger;

    @BeforeEach
    void setUp() {
        runner = mock(CourseInvitationAsyncRunner.class);
        trigger = new CourseInvitationAutoTrigger(runner);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private CourseParticipation participation(String id, String courseCode, boolean autoSend, boolean past) {
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(courseCode);
        course.setAutoSendInvitation(autoSend);
        course.setPastCourse(past);
        CourseParticipation participation = new CourseParticipation();
        participation.setId(id);
        participation.setCourse(course);
        return participation;
    }

    @Test
    void dispatchesImmediatelyWhenThereIsNoTransaction() {
        trigger.afterApproved(List.of(participation("p-1", "KH-1", true, false), participation("p-2", "KH-1", true, false)));
        verify(runner).autoSend("KH-1", List.of("p-1", "p-2"));
    }

    @Test
    void groupsParticipantsByCourse() {
        trigger.afterApproved(List.of(participation("p-1", "KH-1", true, false), participation("p-2", "KH-2", true, false),
                participation("p-3", "KH-1", true, false)));
        verify(runner).autoSend("KH-1", List.of("p-1", "p-3"));
        verify(runner).autoSend("KH-2", List.of("p-2"));
    }

    @Test
    void skipsCoursesWithAutoSendOffAndPastCourses() {
        trigger.afterApproved(List.of(participation("p-1", "KH-OFF", false, false), participation("p-2", "KH-PAST", true, true)));
        verify(runner, never()).autoSend(anyString(), anyList());
    }

    @Test
    void mixedCoursesOnlyDispatchTheOnesWithAutoSend() {
        trigger.afterApproved(List.of(participation("p-1", "KH-OFF", false, false), participation("p-2", "KH-ON", true, false)));
        verify(runner).autoSend("KH-ON", List.of("p-2"));
        verify(runner, never()).autoSend(eq("KH-OFF"), anyList());
    }

    @Test
    void ignoresNullEmptyAndIncompleteInput() {
        trigger.afterApproved(null);
        trigger.afterApproved(List.of());
        CourseParticipation noCourse = new CourseParticipation();
        noCourse.setId("p-x");
        CourseParticipation noId = participation(null, "KH-1", true, false);
        trigger.afterApproved(List.of(noCourse, noId));
        verify(runner, never()).autoSend(any(), any());
    }

    @Test
    void nullFlagMeansOff() {
        CourseParticipation participation = participation("p-1", "KH-1", true, false);
        participation.getCourse().setAutoSendInvitation(null);
        trigger.afterApproved(List.of(participation));
        verify(runner, never()).autoSend(any(), any());
    }

    @Test
    void waitsForCommitWhenInsideATransaction() {
        TransactionSynchronizationManager.initSynchronization();
        trigger.afterApproved(List.of(participation("p-1", "KH-1", true, false)));

        verify(runner, never()).autoSend(any(), any());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.get(0).afterCommit();
        verify(runner).autoSend("KH-1", List.of("p-1"));
    }

    @Test
    void rolledBackTransactionNeverSends() {
        TransactionSynchronizationManager.initSynchronization();
        trigger.afterApproved(List.of(participation("p-1", "KH-1", true, false)));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(runner, never()).autoSend(any(), any());
        assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
    }
}
