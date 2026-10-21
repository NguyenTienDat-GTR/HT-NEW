package phucuong.ht.modules.certificate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService.LevelOrder;
import phucuong.ht.modules.rankHistory.service.RankPromotionService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.service.CourseFinishService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Thu hồi và mở lại chứng nhận đã duyệt, dùng chung cho thu hồi / cấp lại thủ công và cho việc vô hiệu hóa / bật lại khóa.
// Gọi trong transaction của người gọi; người gọi chịu trách nhiệm xóa cache.
@Service
@RequiredArgsConstructor
public class CertificateLifecycleService {

    public static final String COURSE_DISABLED_REASON = "Khóa huấn luyện bị vô hiệu hóa";

    private final CertificateRepository certificateRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final RankPromotionService rankPromotionService;
    private final CourseFinishService courseFinishService;
    private final LeaderLevelService leaderLevelService;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CommonValidationHelper commonValidationHelper;
    private final DomainEventPublisher domainEventPublisher;
    private final CertificateRealtimeNotifier certificateRealtimeNotifier;

    // Cấp độ chứng nhận này trao: hồ sơ ngoại lệ theo cấp người duyệt chọn, còn lại theo cấp của loại khóa.
    public String achievedLevel(Certificate certificate) {
        Optional<CourseParticipation> participation = findParticipation(certificate);
        if (participation.isPresent() && participation.get().getTargetLevel() != null) {
            return participation.get().getTargetLevel();
        }
        return certificate.getCourse() == null || certificate.getCourse().getCourseType() == null
                ? null : certificate.getCourse().getCourseType().getResultingLevel();
    }

    // Thu hồi chứng nhận đã duyệt. Hồ sơ của khóa trở thành không đạt dù điểm cao hơn điểm đạt và huynh trưởng được hạ về
    // cấp trước đó đã đạt. enforceOrder = true (thu hồi thủ công): phải thu hồi từ chứng nhận cấp cao nhất trước.
    public void revoke(Certificate certificate, String reason, CertificateRevocationSource source, boolean enforceOrder,
                       String actor, OffsetDateTime now) {
        requireLinked(certificate);
        if (certificate.getApprovalStatus() == WorkflowApprovalStatus.REVOKED) {
            throw commonValidationHelper.conflict("Chứng nhận đã bị thu hồi trước đó");
        }
        if (certificate.getApprovalStatus() != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Chỉ thu hồi được chứng nhận đã duyệt");
        }
        if (enforceOrder) {
            requireNoHigherActiveCertificate(certificate);
        }
        certificate.setApprovalStatus(WorkflowApprovalStatus.REVOKED);
        certificate.setRevokedAt(now);
        certificate.setRevokedBy(actor);
        certificate.setRevocationReason(reason);
        certificate.setRevocationSource(source);
        certificate.setReissuedAt(null);
        certificate.setReissuedBy(null);
        certificate.setReissueReason(null);
        certificate.setUpdatedAt(now);
        certificate.setUpdatedBy(actor);

        findParticipation(certificate).ifPresent(participation -> {
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.REVOKED);
            // Không đạt bất kể điểm: chốt ngay kết quả chung của hồ sơ, kể cả khi khóa chưa kết thúc.
            participation.setFinalResult(CourseFinalResult.FAILED);
            participation.setUpdatedAt(now);
            participation.setUpdatedBy(actor);
        });
        // Hạ cấp về cấp trước đó huynh trưởng đã đạt (nếu khóa này đã thăng cấp cho huynh trưởng).
        rankPromotionService.revertPromotion(certificate.getLeader(), certificate.getCourse().getCourseCode(),
                source == CertificateRevocationSource.COURSE_DISABLED
                        ? "Hạ cấp do khóa bị vô hiệu hóa, thu hồi chứng nhận " + certificate.getCertificateCode()
                        : "Hạ cấp do thu hồi chứng nhận " + certificate.getCertificateCode(),
                actor, now);
        publish("training.certificate.revoked", certificate, actor, Map.of(
                "courseCode", certificate.getCourse().getCourseCode(), "source", source.name()));
        certificateRealtimeNotifier.notifyChanged("REVOKED", certificate.getCourse().getCourseCode(), certificate.getCertificateCode());
    }

    // Mở lại chứng nhận đã bị thu hồi: trả về Đã duyệt, hồ sơ trở lại đạt (nếu khóa đã kết thúc thì đánh giá lại và thăng cấp lại).
    // reissue = true (cấp lại thủ công): ghi người cấp lại và lý do, phải cấp từ chứng nhận cấp thấp nhất trước.
    // reissue = false (bật lại khóa): mở lại tất cả chứng nhận bị thu hồi do vô hiệu hóa, không ràng buộc thứ tự.
    public void restore(Certificate certificate, String reason, boolean reissue, String actor, OffsetDateTime now) {
        requireLinked(certificate);
        if (certificate.getApprovalStatus() != WorkflowApprovalStatus.REVOKED) {
            throw commonValidationHelper.conflict("Chỉ cấp lại được chứng nhận đã bị thu hồi");
        }
        if (reissue) {
            if (certificate.getRevocationSource() == CertificateRevocationSource.COURSE_DISABLED) {
                throw commonValidationHelper.conflict(
                        "Chứng nhận bị thu hồi do khóa huấn luyện bị vô hiệu hóa, hãy bật lại khóa để mở lại chứng nhận");
            }
            requireNoLowerRevokedCertificate(certificate);
        }
        certificate.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        certificate.setRevocationSource(null);
        if (reissue) {
            certificate.setReissuedAt(now);
            certificate.setReissuedBy(actor);
            certificate.setReissueReason(reason);
        }
        certificate.setUpdatedAt(now);
        certificate.setUpdatedBy(actor);

        findParticipation(certificate).ifPresent(participation -> {
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
            participation.setUpdatedAt(now);
            participation.setUpdatedBy(actor);
            if (participation.getCourse() != null && participation.getCourse().getFinishedAt() != null) {
                // Khóa đã kết thúc: đánh giá lại kết quả chung và thăng cấp lại nếu hồ sơ đạt.
                courseFinishService.reevaluateIfFinished(participation, actor);
            } else {
                // Khóa chưa kết thúc: kết quả chung chỉ được chốt khi kết thúc khóa.
                participation.setFinalResult(null);
            }
        });
        publish(reissue ? "training.certificate.reissued" : "training.certificate.restored", certificate, actor,
                Map.of("courseCode", certificate.getCourse().getCourseCode()));
        certificateRealtimeNotifier.notifyChanged(reissue ? "REISSUED" : "RESTORED",
                certificate.getCourse().getCourseCode(), certificate.getCertificateCode());
    }

    // Đang giữ chứng nhận đã duyệt có cấp cao hơn thì chưa được thu hồi chứng nhận cấp thấp hơn.
    private void requireNoHigherActiveCertificate(Certificate certificate) {
        LevelOrder order = levelOrderOf(certificate);
        String achieved = achievedLevel(certificate);
        for (Certificate other : certificateRepository.findOthersByLeader(certificate.getLeader().getId(), certificate.getCertificateCode())) {
            if (other.getApprovalStatus() == WorkflowApprovalStatus.APPROVED && order.higher(achievedLevel(other), achieved)) {
                throw commonValidationHelper.conflict("Huynh trưởng đang giữ chứng nhận cấp cao hơn (" + describe(other, order)
                        + "). Phải thu hồi chứng nhận cấp cao nhất trước.");
            }
        }
    }

    // Còn chứng nhận cấp thấp hơn đang bị thu hồi thì chưa được cấp lại chứng nhận cấp cao hơn.
    private void requireNoLowerRevokedCertificate(Certificate certificate) {
        LevelOrder order = levelOrderOf(certificate);
        String achieved = achievedLevel(certificate);
        for (Certificate other : certificateRepository.findOthersByLeader(certificate.getLeader().getId(), certificate.getCertificateCode())) {
            if (other.getApprovalStatus() == WorkflowApprovalStatus.REVOKED && order.higher(achieved, achievedLevel(other))) {
                String hint = other.getRevocationSource() == CertificateRevocationSource.COURSE_DISABLED
                        ? " (đang bị thu hồi do khóa bị vô hiệu hóa, cần bật lại khóa)" : "";
                throw commonValidationHelper.conflict("Chứng nhận cấp thấp hơn của huynh trưởng đang bị thu hồi (" + describe(other, order)
                        + ")" + hint + ". Phải cấp lại từ chứng nhận cấp thấp nhất trước.");
            }
        }
    }

    private String describe(Certificate other, LevelOrder order) {
        String course = other.getCourse() == null ? "" : other.getCourse().getCourseName();
        return other.getCertificateCode() + " - " + order.nameOf(achievedLevel(other)) + (course == null || course.isBlank() ? "" : ", khóa " + course);
    }

    private LevelOrder levelOrderOf(Certificate certificate) {
        return leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(certificate.getCourse()));
    }

    private Optional<CourseParticipation> findParticipation(Certificate certificate) {
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            return Optional.empty();
        }
        return courseParticipationRepository.findByLeaderIdAndCourseCode(certificate.getLeader().getId(), certificate.getCourse().getCourseCode());
    }

    private void requireLinked(Certificate certificate) {
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            throw commonValidationHelper.validationError("Chứng chỉ phải được liên kết với leader và khóa huấn luyện");
        }
    }

    private void publish(String eventType, Certificate certificate, String actor, Map<String, Object> payload) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.builder()
                .eventType(eventType)
                .entityType("certificate")
                .entityId(certificate.getCertificateCode())
                .actor(actor)
                .occurredAt(OffsetDateTime.now())
                .payload(payload)
                .build());
    }

    // Số chứng nhận đã duyệt / đang chờ mở lại của khóa, để cảnh báo trước khi vô hiệu hóa / bật lại khóa.
    public List<Certificate> approvedOf(String courseCode) {
        return certificateRepository.findByCourseAndStatus(courseCode, WorkflowApprovalStatus.APPROVED);
    }

    public List<Certificate> revokedByCourseDisableOf(String courseCode) {
        return certificateRepository.findRevokedByCourseAndSource(courseCode, CertificateRevocationSource.COURSE_DISABLED);
    }
}
