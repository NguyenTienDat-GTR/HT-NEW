package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Kết thúc khóa huấn luyện và chốt kết quả chung của từng hồ sơ.
 * Hồ sơ không đạt khi có kết quả chưa đạt, chứng nhận chưa được duyệt hoặc chứng nhận bị từ chối: không thăng cấp.
 * Ngược lại hồ sơ đạt và được thăng cấp theo cấu hình (resultingLevel) của loại khóa.
 */
@Service
@RequiredArgsConstructor
public class CourseFinishService {

    private final CourseParticipationRepository courseParticipationRepository;
    private final CertificateRepository certificateRepository;
    private final RankHistoryRepository rankHistoryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    // Gọi trong transaction của người gọi. Trả về số hồ sơ đạt.
    public int finish(TrainingCourse course, String actor, OffsetDateTime now) {
        int passed = 0;
        List<CourseParticipation> participations = courseParticipationRepository.findAllByCourseCode(course.getCourseCode());
        for (CourseParticipation participation : participations) {
            if (!isEvaluable(participation)) {
                continue;
            }
            if (evaluate(participation, course, actor, now) == CourseFinalResult.PASSED) {
                passed++;
            }
        }
        course.setFinishedAt(now);
        course.setFinishedBy(actor);
        course.setOrganizingStatus(CourseOrganizingStatus.ENDED);
        // Chốt điểm: khóa đã kết thúc thì không sửa điểm nữa.
        course.setScoreLocked(Boolean.TRUE);
        course.setUpdatedAt(now);
        course.setUpdatedBy(actor);
        return passed;
    }

    // Đánh giá lại một hồ sơ khi chứng nhận được duyệt hoặc từ chối sau khi khóa đã kết thúc.
    public void reevaluateIfFinished(CourseParticipation participation, String actor) {
        TrainingCourse course = participation.getCourse();
        if (course == null || course.getFinishedAt() == null || !isEvaluable(participation)) {
            return;
        }
        evaluate(participation, course, actor, OffsetDateTime.now());
    }

    private boolean isEvaluable(CourseParticipation participation) {
        return participation.getApprovalStatus() == WorkflowApprovalStatus.APPROVED
                && participation.getParticipationStatus() != ParticipationStatus.CANCELLED;
    }

    private CourseFinalResult evaluate(CourseParticipation participation, TrainingCourse course, String actor, OffsetDateTime now) {
        Optional<Certificate> certificate = certificateRepository.findByLeaderIdAndCourseCode(
                participation.getLeader().getId(), course.getCourseCode());
        boolean scorePassed = participation.getResult() == CourseResult.COMPLETED;
        boolean certificateApproved = certificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.APPROVED).orElse(false);
        // Khóa không có cấp độ đạt được không cần chứng nhận: chỉ xét kết quả điểm.
        boolean certificateRequired = trainingWorkflowSupport.requiresCertificate(course.getCourseType());
        CourseFinalResult result = scorePassed && (!certificateRequired || certificateApproved) ? CourseFinalResult.PASSED : CourseFinalResult.FAILED;
        CourseFinalResult before = participation.getFinalResult();
        participation.setFinalResult(result);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        if (result == CourseFinalResult.PASSED && before != CourseFinalResult.PASSED) {
            trainingWorkflowSupport.certificateLevelFor(course.getCourseType())
                    .filter(level -> trainingWorkflowSupport.isPromotion(participation.getLeader().getLeaderLevel(), level))
                    .ifPresent(level -> promoteLeader(participation.getLeader(), level, actor, course.getCourseCode(), now));
        }
        return result;
    }

    private void promoteLeader(Leader leader, LeaderLevel newLevel, String actor, String courseCode, OffsetDateTime now) {
        if (leader.getLeaderLevel() == newLevel) {
            return;
        }
        RankHistory rankHistory = new RankHistory();
        rankHistory.setLeader(leader);
        rankHistory.setOldLevel(leader.getLeaderLevel());
        rankHistory.setNewLevel(newLevel);
        rankHistory.setPromotionDate(LocalDate.now());
        rankHistory.setNote("Thăng cấp khi kết thúc khóa " + courseCode);
        rankHistory.setStatus(Boolean.TRUE);
        rankHistory.setCreatedAt(now);
        rankHistory.setUpdatedAt(now);
        rankHistory.setCreatedBy(actor);
        rankHistory.setUpdatedBy(actor);
        rankHistoryRepository.save(rankHistory);
        leader.setLeaderLevel(newLevel);
        leader.setUpdatedAt(now);
        leader.setUpdatedBy(actor);
    }
}
