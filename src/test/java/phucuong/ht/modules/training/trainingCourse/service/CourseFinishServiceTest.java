package phucuong.ht.modules.training.trainingCourse.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService.LevelOrder;
import phucuong.ht.modules.rankHistory.service.RankPromotionService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Kết thúc khóa: khóa có cấp độ đạt được chỉ xét chứng nhận đã duyệt (không xét điểm); chứng nhận chưa duyệt bị từ chối.
class CourseFinishServiceTest {

    private CourseParticipationRepository participationRepository;
    private CertificateRepository certificateRepository;
    private RankPromotionService rankPromotionService;
    private CourseFinishService service;
    private TrainingCourse course;
    private Leader leader;
    private CourseParticipation participation;

    @BeforeEach
    void setUp() {
        participationRepository = mock(CourseParticipationRepository.class);
        certificateRepository = mock(CertificateRepository.class);
        rankPromotionService = mock(RankPromotionService.class);
        LeaderLevelService leaderLevelService = mock(LeaderLevelService.class);
        when(leaderLevelService.order(any())).thenReturn(new LevelOrder(Map.of("NONE", 0, "HT_I", 1), Map.of()));
        service = new CourseFinishService(participationRepository, certificateRepository, leaderLevelService, rankPromotionService, new TrainingWorkflowSupport());

        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("T1");
        type.setResultingLevel("HT_I");
        course = new TrainingCourse();
        course.setCourseCode("K1");
        course.setCourseType(type);
        leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setLeaderLevel("NONE");
        participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setParticipationStatus(ParticipationStatus.ATTENDED);
        participation.setResult(CourseResult.UNCOMPLETED);
        when(participationRepository.findAllByCourseCode("K1")).thenReturn(List.of(participation));
    }

    private Certificate certificate(WorkflowApprovalStatus status) {
        Certificate certificate = new Certificate();
        certificate.setApprovalStatus(status);
        when(certificateRepository.findByLeaderIdAndCourseCode(leader.getId(), "K1")).thenReturn(Optional.of(certificate));
        return certificate;
    }

    @Test
    void approvedExceptionCertificatePromotesEvenWhenTheScoreFailed() {
        certificate(WorkflowApprovalStatus.APPROVED);

        int passed = service.finish(course, "admin", OffsetDateTime.now());

        assertEquals(1, passed);
        assertEquals(CourseFinalResult.PASSED, participation.getFinalResult());
        verify(rankPromotionService).promote(eq(leader), eq("HT_I"), eq("K1"), eq("admin"), any());
    }

    @Test
    void pendingCertificateIsRejectedWhenTheCourseFinishesAndNobodyIsPromoted() {
        participation.setResult(CourseResult.COMPLETED);
        Certificate pending = certificate(WorkflowApprovalStatus.PENDING);

        int passed = service.finish(course, "admin", OffsetDateTime.now());

        assertEquals(0, passed);
        assertEquals(WorkflowApprovalStatus.REJECTED, pending.getApprovalStatus());
        assertNotNull(pending.getRejectionReason());
        assertEquals(WorkflowApprovalStatus.REJECTED, participation.getCertificateApprovalStatus());
        assertEquals(CourseFinalResult.FAILED, participation.getFinalResult());
        verify(rankPromotionService, never()).promote(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void courseWithoutResultingLevelStillUsesTheScoreResult() {
        course.getCourseType().setResultingLevel(null);
        participation.setResult(CourseResult.COMPLETED);

        int passed = service.finish(course, "admin", OffsetDateTime.now());

        assertEquals(1, passed);
        assertEquals(CourseFinalResult.PASSED, participation.getFinalResult());
        verify(rankPromotionService, never()).promote(any(), anyString(), anyString(), anyString(), any());
    }
}
