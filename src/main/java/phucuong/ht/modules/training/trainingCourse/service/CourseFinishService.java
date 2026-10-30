package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService.LevelOrder;
import phucuong.ht.modules.rankHistory.service.RankPromotionService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

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
    private final LeaderLevelService leaderLevelService;
    private final RankPromotionService rankPromotionService;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    // Khóa hàng huynh trưởng trước khi so sánh / đổi cấp độ (tuỳ chọn để test đơn vị không cần).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;

    // Gọi trong transaction của người gọi. Trả về số hồ sơ đạt.
    public int finish(TrainingCourse course, String actor, OffsetDateTime now) {
        int passed = 0;
        LevelOrder levelOrder = leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));
        List<CourseParticipation> participations = courseParticipationRepository.findAllByCourseCode(course.getCourseCode());
        for (CourseParticipation participation : participations) {
            if (!isEvaluable(participation)) {
                continue;
            }
            if (evaluate(participation, course, actor, now, levelOrder) == CourseFinalResult.PASSED) {
                passed++;
            }
        }
        course.setFinishedAt(now);
        course.setFinishedBy(actor);
        course.setOrganizingStatus(CourseOrganizingStatus.ENDED);
        // Khóa đã kết thúc thì đóng đăng ký.
        course.setRegistrationStatus(phucuong.ht.modules.training.enums.CourseRegistrationStatus.ENDED);
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
        evaluate(participation, course, actor, OffsetDateTime.now(), leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course)));
    }

    private boolean isEvaluable(CourseParticipation participation) {
        return participation.getApprovalStatus() == WorkflowApprovalStatus.APPROVED
                && participation.getParticipationStatus() != ParticipationStatus.CANCELLED;
    }

    private CourseFinalResult evaluate(CourseParticipation participation, TrainingCourse course, String actor, OffsetDateTime now, LevelOrder levelOrder) {
        Optional<Certificate> certificate = certificateRepository.findByLeaderIdAndCourseCode(
                participation.getLeader().getId(), course.getCourseCode());
        boolean scorePassed = participation.getResult() == CourseResult.COMPLETED;
        boolean certificateApproved = certificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.APPROVED).orElse(false);
        // Khóa không có cấp độ đạt được không cần chứng nhận: chỉ xét kết quả điểm.
        boolean certificateRequired = trainingWorkflowSupport.requiresCertificate(course);
        CourseFinalResult result = scorePassed && (!certificateRequired || certificateApproved) ? CourseFinalResult.PASSED : CourseFinalResult.FAILED;
        CourseFinalResult before = participation.getFinalResult();
        participation.setFinalResult(result);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        if (result == CourseFinalResult.PASSED && before != CourseFinalResult.PASSED) {
            // Hai chứng nhận / hai lần kết thúc khóa cùng đổi cấp một huynh trưởng: khóa hàng để không mất cập nhật cấp độ.
            if (entityLockService != null) {
                entityLockService.lock(participation.getLeader());
            }
            // Cấp đạt được: hồ sơ ngoại lệ theo cấp người duyệt đã chọn, còn lại theo cấp của loại khóa.
            String achieved = participation.getTargetLevel() != null
                    ? participation.getTargetLevel()
                    : trainingWorkflowSupport.certificateLevelFor(course).orElse(null);
            if (achieved != null && course.isPast()) {
                // Khóa quá khứ: luôn ghi lịch sử cấp độ theo ngày duyệt chứng nhận; cấp hiện tại chỉ tăng khi cấp mới cao hơn.
                java.time.LocalDate approvedDate = participation.getCertificateApprovedAt() == null ? null
                        : participation.getCertificateApprovedAt().atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
                rankPromotionService.recordPastCourse(participation.getLeader(), achieved,
                        levelOrder.higher(achieved, participation.getLeader().getLeaderLevel()),
                        course.getCourseCode(), approvedDate, actor, now);
            } else if (achieved != null && levelOrder.higher(achieved, participation.getLeader().getLeaderLevel())) {
                rankPromotionService.promote(participation.getLeader(), achieved, course.getCourseCode(), actor, now);
            }
        }
        return result;
    }
}
