package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseParticipationService {

    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final LeaderRepository leaderRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final DomainEventPublisher domainEventPublisher;

    public CourseParticipationResponse register(String courseCode, TrainingRegistrationRequest request, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        Leader leader = leaderRepository.findByIdWithParish(request.getLeaderId())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy leader " + request.getLeaderId()));

        UUID activeParishId = accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
        if (leader.getParish() == null || !activeParishId.equals(leader.getParish().getId())) {
            throw commonValidationHelper.forbidden("Không thể đăng ký leader ngoài giáo xứ hiện tại");
        }

        boolean eligible = isEligible(leader, course);
        if (!eligible && (request.getExceptionReason() == null || request.getExceptionReason().isBlank())) {
            throw commonValidationHelper.validationError("Leader không đủ điều kiện, cần nhập exceptionReason");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gửi danh sách");
        CourseParticipation participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setRegistrationDate(LocalDate.now());
        participation.setParticipationStatus(ParticipationStatus.REGISTERED);
        participation.setResult(CourseResult.UNCOMPLETED);
        participation.setReview(null);
        participation.setEligible(eligible);
        participation.setExceptionReason(commonValidationHelper.normalizeOptionalText(request.getExceptionReason()));
        participation.setParishSubmittedAt(OffsetDateTime.now());
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
        participation.setCreatedBy(actor);
        participation.setUpdatedBy(actor);
        participation.setStatus(Boolean.TRUE);
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            participation.setDeaneryApprovalStatus(eligible ? WorkflowApprovalStatus.NOT_REQUIRED : WorkflowApprovalStatus.PENDING);
            participation.setDioceseApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
        } else {
            participation.setDeaneryApprovalStatus(WorkflowApprovalStatus.SUBMITTED);
            participation.setDioceseApprovalStatus(eligible ? WorkflowApprovalStatus.NOT_REQUIRED : WorkflowApprovalStatus.PENDING);
        }
        courseParticipationRepository.save(participation);
        publishEvent("training.registration.submitted", "courseParticipation", participation.getId(), actor, Map.of("courseCode", courseCode));
        return toResponse(participation);
    }

    public List<CourseParticipationResponse> deanerySubmit(String courseCode, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            throw commonValidationHelper.validationError("Khóa DT không dùng luồng deanery-submit");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tổng hợp danh sách");
        List<CourseParticipation> participations = courseParticipationRepository.findAllByCourseCode(courseCode);
        participations.forEach(participation -> {
            participation.setDeanerySubmittedAt(OffsetDateTime.now());
            participation.setUpdatedBy(actor);
        });
        publishEvent("training.registration.deanery_submitted", "trainingCourse", courseCode, actor, Map.of("count", participations.size()));
        return participations.stream().map(this::toResponse).toList();
    }

    public CourseParticipationResponse deaneryApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        if (!trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
            throw commonValidationHelper.validationError("Chỉ khóa DT mới được deanery approval");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo hạt");
        participation.setDeaneryApprovalStatus(Boolean.TRUE.equals(request.getApproved()) ? WorkflowApprovalStatus.APPROVED : WorkflowApprovalStatus.REJECTED);
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.deanery_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        return toResponse(participation);
    }

    public CourseParticipationResponse dioceseApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
            throw commonValidationHelper.validationError("Khóa DT không dùng diocese approval");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo phận");
        participation.setDioceseApprovalStatus(Boolean.TRUE.equals(request.getApproved()) ? WorkflowApprovalStatus.APPROVED : WorkflowApprovalStatus.REJECTED);
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.diocese_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        return toResponse(participation);
    }

    public CourseParticipationResponse score(String participationId, TrainingScoreRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người nhập điểm");
        participation.setTotalScore(request.getTotalScore());
        participation.setReview(request.getReview());
        participation.setUpdatedBy(actor);
        if (request.getTotalScore().compareTo(BigDecimal.valueOf(5)) >= 0) {
            participation.setResult(CourseResult.COMPLETED);
            participation.setParticipationStatus(ParticipationStatus.COMPLETED);
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
            participation.setCertificateRequestedAt(OffsetDateTime.now());
        } else {
            participation.setResult(CourseResult.UNCOMPLETED);
            participation.setParticipationStatus(ParticipationStatus.ATTENDED);
        }
        publishEvent("training.score.updated", "courseParticipation", participationId, actor, Map.of("score", request.getTotalScore()));
        return toResponse(participation);
    }

    private boolean isEligible(Leader leader, TrainingCourse course) {
        CourseRequirement requirement = course.getRequirement();
        if (requirement != null) {
            if (requirement.getRequiredCurrentLevel() != null && leader.getLeaderLevel() != requirement.getRequiredCurrentLevel()) {
                return false;
            }
            if (requirement.getRequiredMinAge() != null && leader.getBirthDate() != null) {
                int age = Period.between(leader.getBirthDate(), LocalDate.now()).getYears();
                if (age < requirement.getRequiredMinAge()) {
                    return false;
                }
            }
            if (requirement.getRequiredYearsActive() != null && leader.getCreatedAt() != null) {
                int yearsActive = Period.between(leader.getCreatedAt().toLocalDate(), LocalDate.now()).getYears();
                if (yearsActive < requirement.getRequiredYearsActive()) {
                    return false;
                }
            }
        }

        return trainingWorkflowSupport.prerequisiteOf(course.getCourseType())
                .map(prerequisite -> courseParticipationRepository.existsApprovedParticipationByLeaderAndCourseType(leader.getId(), prerequisite))
                .orElse(true);
    }

    private CourseParticipationResponse toResponse(CourseParticipation participation) {
        return CourseParticipationResponse.builder()
                .id(participation.getId())
                .leaderId(participation.getLeader() == null ? null : participation.getLeader().getId())
                .courseCode(participation.getCourse() == null ? null : participation.getCourse().getCourseCode())
                .courseType(participation.getCourse() == null ? null : participation.getCourse().getCourseType())
                .registrationDate(participation.getRegistrationDate())
                .participationStatus(participation.getParticipationStatus())
                .totalScore(participation.getTotalScore())
                .result(participation.getResult())
                .review(participation.getReview())
                .eligible(participation.getEligible())
                .exceptionReason(participation.getExceptionReason())
                .parishSubmittedAt(participation.getParishSubmittedAt())
                .deanerySubmittedAt(participation.getDeanerySubmittedAt())
                .deaneryApprovalStatus(participation.getDeaneryApprovalStatus())
                .dioceseApprovalStatus(participation.getDioceseApprovalStatus())
                .certificateApprovalStatus(participation.getCertificateApprovalStatus())
                .certificateRequestedAt(participation.getCertificateRequestedAt())
                .certificateApprovedAt(participation.getCertificateApprovedAt())
                .approvedBy(participation.getApprovedBy())
                .status(participation.getStatus())
                .build();
    }

    private void publishEvent(String eventType, String entityType, String entityId, String actor, Map<String, Object> payload) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .occurredAt(OffsetDateTime.now())
                .payload(payload)
                .build());
    }
}
