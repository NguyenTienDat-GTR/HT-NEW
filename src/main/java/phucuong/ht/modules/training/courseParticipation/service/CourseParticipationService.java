package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.certificate.CertificateCodeGenerator;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.TrainingScoreFormulaEvaluator;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipationComponentScore;
import phucuong.ht.modules.training.courseParticipation.model.request.ComponentScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingSubmissionRecipientRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.ComponentScoreResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingRegistrationBatchResponse;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaComponent;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseParticipationService {

    private static final String AUTO_APPROVAL_SYSTEM_ACTOR = "SYSTEM_AUTO_APPROVAL";
    private static final String EVENT_BATCH_SUBMITTED = "training.registration.batch_submitted";
    private static final String EVENT_DEANERY_BATCH_SUBMITTED = "training.registration.deanery_batch_submitted";

    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final TrainingScoreFormulaEvaluator trainingScoreFormulaEvaluator;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final CertificateRepository certificateRepository;
    private final CertificateCodeGenerator certificateCodeGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final TrainingNotificationService trainingNotificationService;

    @Transactional(readOnly = true)
    public Page<CourseParticipationResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scoped = copySearchRequest(request);
        if (hasAuthority(authentication, CourseParticipationAccessEvaluator.READ_DIOCESE)
                || hasAuthority(authentication, CourseParticipationAccessEvaluator.APPROVE_DIOCESE)
                || hasAuthority(authentication, CourseParticipationAccessEvaluator.SCORE_DIOCESE)) {
            scoped.getFilters().put("course.diocese.id", accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseParticipationAccessEvaluator.APPROVE_DIOCESE));
        } else if (hasAuthority(authentication, CourseParticipationAccessEvaluator.READ_DEANERY)
                || hasAuthority(authentication, CourseParticipationAccessEvaluator.APPROVE_DEANERY)
                || hasAuthority(authentication, CourseParticipationAccessEvaluator.SCORE_DEANERY)) {
            scoped.getFilters().put("course.deanery.id", accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.APPROVE_DEANERY));
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem hồ sơ đào tạo");
        }
        Page<CourseParticipation> page = courseParticipationRepository.findAll(scoped);
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CourseParticipationResponse findById(String participationId) {
        return toResponse(courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ đào tạo: " + participationId)));
    }

    @Transactional
    public TrainingRegistrationBatchResponse register(String courseCode, TrainingRegistrationRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(courseCode);
        requireRegistrationWindowOpen(course);

        UUID activeParishId = accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
        Parish parish = parishRepository.findByIdWithDeaneryAndDiocese(activeParishId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ " + activeParishId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gửi danh sách");
        OffsetDateTime submittedAt = OffsetDateTime.now();

        List<CourseParticipation> createdParticipations = new ArrayList<>();
        for (UUID leaderId : normalizeLeaderIds(request.getLeaderIds())) {
            Leader leader = leaderRepository.findByIdWithParish(leaderId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy leader " + leaderId));
            if (leader.getParish() == null || !activeParishId.equals(leader.getParish().getId())) {
                throw commonValidationHelper.forbidden("Không thể đăng ký leader ngoài giáo xứ hiện tại");
            }

            if (courseParticipationRepository.existsByLeaderIdAndCourseCode(leaderId, courseCode)) {
                throw commonValidationHelper.conflict("Leader đã có hồ sơ tham gia khóa huấn luyện này");
            }
            boolean eligible = isEligible(leader, course);
            String exceptionReason = resolveExceptionReason(request.getExceptionReasonsByLeaderId(), leaderId);
            if (!eligible && (exceptionReason == null || exceptionReason.isBlank())) {
                throw commonValidationHelper.validationError("Leader không đủ điều kiện, cần nhập exceptionReason");
            }

            CourseParticipation participation = new CourseParticipation();
            participation.setLeader(leader);
            participation.setCourse(course);
            participation.setRegistrationDate(LocalDate.now());
            participation.setParticipationStatus(ParticipationStatus.REGISTERED);
            participation.setResult(CourseResult.UNCOMPLETED);
            participation.setReview(null);
            participation.setEligible(eligible);
            participation.setExceptionReason(commonValidationHelper.normalizeOptionalText(exceptionReason));
            participation.setParishSubmittedAt(submittedAt);
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
            participation.setCreatedBy(actor);
            participation.setUpdatedBy(actor);
            participation.setStatus(Boolean.TRUE);
            if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
                participation.setDeaneryApprovalStatus(WorkflowApprovalStatus.PENDING);
                participation.setDioceseApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
            } else {
                participation.setDeaneryApprovalStatus(WorkflowApprovalStatus.SUBMITTED);
                participation.setDioceseApprovalStatus(WorkflowApprovalStatus.PENDING);
            }
            courseParticipationRepository.save(participation);
            createdParticipations.add(participation);
        }

        List<String> recipients = notificationRecipientResolver.resolveRecipients(
                request.getRecipientUsernames(),
                submitNotificationPermission(course),
                UnitType.DEANERY,
                parish.getDeanery().getId()
        );
        trainingNotificationService.notifyUsers(recipients, buildSubmissionNotification(
                "training.registration.submitted",
                parish.getName(),
                UnitType.PARISH,
                parish.getId(),
                courseCode,
                createdParticipations.size(),
                actor
        ));

        publishBatchEvent(
                EVENT_BATCH_SUBMITTED,
                course,
                createdParticipations,
                UnitType.PARISH,
                parish.getId(),
                parish.getName(),
                actor,
                UnitType.DEANERY,
                parish.getDeanery().getId(),
                submitNotificationPermission(course)
        );

        return buildBatchResponse(course, UnitType.PARISH, parish.getId(), parish.getName(), createdParticipations, previewParishSubmissionAutoApprovedCount(course, createdParticipations));
    }

    @Transactional
    public TrainingRegistrationBatchResponse deanerySubmit(String courseCode, TrainingSubmissionRecipientRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(courseCode);
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            throw commonValidationHelper.validationError("Khóa DT không dùng luồng deanery-submit");
        }

        UUID activeDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
        Deanery deanery = deaneryRepository.findByIdWithDiocese(activeDeaneryId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt " + activeDeaneryId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tổng hợp danh sách");
        OffsetDateTime submittedAt = OffsetDateTime.now();

        List<CourseParticipation> participations = courseParticipationRepository.findAllByCourseCodeAndDeaneryId(courseCode, activeDeaneryId);
        if (participations.isEmpty()) {
            throw commonValidationHelper.validationError("Không có đăng ký nào để tổng hợp");
        }

        participations.forEach(participation -> {
            participation.setDeanerySubmittedAt(submittedAt);
            participation.setUpdatedBy(actor);
        });

        List<String> recipients = notificationRecipientResolver.resolveRecipients(
                request == null ? null : request.getRecipientUsernames(),
                CourseParticipationAccessEvaluator.APPROVE_DIOCESE,
                UnitType.DIOCESE,
                deanery.getDiocese().getId()
        );
        trainingNotificationService.notifyUsers(recipients, buildSubmissionNotification(
                "training.registration.deanery_submitted",
                deanery.getName(),
                UnitType.DEANERY,
                deanery.getId(),
                courseCode,
                participations.size(),
                actor
        ));

        publishBatchEvent(
                EVENT_DEANERY_BATCH_SUBMITTED,
                course,
                participations,
                UnitType.DEANERY,
                deanery.getId(),
                deanery.getName(),
                actor,
                UnitType.DIOCESE,
                deanery.getDiocese().getId(),
                CourseParticipationAccessEvaluator.APPROVE_DIOCESE
        );

        return buildBatchResponse(course, UnitType.DEANERY, deanery.getId(), deanery.getName(), participations, previewDeanerySubmissionAutoApprovedCount(course, participations));
    }

    @Transactional
    public CourseParticipationResponse deaneryApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        if (!trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
            throw commonValidationHelper.validationError("Chỉ khóa DT mới dùng deanery approval");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo hạt");
        requirePendingManualApproval(participation.getDeaneryApprovalStatus());
        participation.setDeaneryApprovalStatus(Boolean.TRUE.equals(request.getApproved()) ? WorkflowApprovalStatus.APPROVED : WorkflowApprovalStatus.REJECTED);
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.deanery_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        return toResponse(participation);
    }

    @Transactional
    public CourseParticipationResponse dioceseApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
            throw commonValidationHelper.validationError("Khóa DT không dùng diocese approval");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo phận");
        requirePendingManualApproval(participation.getDioceseApprovalStatus());
        participation.setDioceseApprovalStatus(Boolean.TRUE.equals(request.getApproved()) ? WorkflowApprovalStatus.APPROVED : WorkflowApprovalStatus.REJECTED);
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.diocese_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        return toResponse(participation);
    }

    @Transactional
    public CourseParticipationResponse score(String participationId, TrainingScoreRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người nhập điểm");

        if (participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Không thể sửa điểm sau khi chứng chỉ đã được duyệt");
        }
        requireApprovedForScoring(participation);
        ScoreFormulaTemplate scoreFormula = requireScoreFormula(participation);
        Map<String, ComponentScoreRequest> requestedScores = normalizeComponentScores(request.getComponentScores());
        Map<String, BigDecimal> formulaVariables = new LinkedHashMap<>();

        participation.getComponentScores().clear();
        List<ScoreFormulaComponent> formulaComponents = scoreFormula.getComponents().stream()
                .sorted(Comparator.comparing(ScoreFormulaComponent::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        for (ScoreFormulaComponent formulaComponent : formulaComponents) {
            ComponentScoreRequest componentRequest = requestedScores.remove(formulaComponent.getComponent().getId());
            if (componentRequest == null) {
                throw commonValidationHelper.validationError("Thiếu điểm cho thành phần bắt buộc " + formulaComponent.getComponent().getCode());
            }
            if (componentRequest.getScore().compareTo(formulaComponent.getComponent().getMaxScore()) > 0) {
                throw commonValidationHelper.validationError("Điểm vượt quá maxScore của thành phần " + formulaComponent.getComponent().getCode());
            }

            CourseParticipationComponentScore persistedScore = new CourseParticipationComponentScore();
            persistedScore.setParticipation(participation);
            persistedScore.setComponent(formulaComponent.getComponent());
            persistedScore.setScore(componentRequest.getScore());
            persistedScore.setNote(commonValidationHelper.normalizeOptionalText(componentRequest.getNote()));
            persistedScore.setStatus(Boolean.TRUE);
            persistedScore.setCreatedBy(actor);
            persistedScore.setUpdatedBy(actor);
            participation.getComponentScores().add(persistedScore);
            formulaVariables.put(formulaComponent.getComponent().getCode(), componentRequest.getScore());
        }
        if (!requestedScores.isEmpty()) {
            throw commonValidationHelper.validationError("componentScores gửi lên có thành phần ngoài công thức điểm của khóa học");
        }

        BigDecimal totalScore = trainingScoreFormulaEvaluator.evaluate(scoreFormula.getExpression(), formulaVariables);
        participation.setTotalScore(totalScore);
        participation.setReview(request.getReview());
        participation.setUpdatedBy(actor);
        if (totalScore.compareTo(scoreFormula.getPassingScore()) >= 0) {
            participation.setResult(CourseResult.COMPLETED);
            participation.setParticipationStatus(ParticipationStatus.COMPLETED);
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
            if (participation.getCertificateRequestedAt() == null) {
                participation.setCertificateRequestedAt(OffsetDateTime.now());
            }
            ensureSuggestedCertificateExists(participation, actor);
        } else {
            participation.setResult(CourseResult.UNCOMPLETED);
            participation.setParticipationStatus(ParticipationStatus.ATTENDED);
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
            participation.setCertificateRequestedAt(null);
        }
        publishEvent("training.score.updated", "courseParticipation", participationId, actor, Map.of("score", totalScore));
        return toResponse(participation);
    }

    private ScoreFormulaTemplate requireScoreFormula(CourseParticipation participation) {
        if (participation.getCourse() == null || participation.getCourse().getScoreFormula() == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa có công thức tính điểm");
        }
        ScoreFormulaTemplate formula = participation.getCourse().getScoreFormula();
        if (formula.getComponents() == null || formula.getComponents().isEmpty()) {
            throw commonValidationHelper.validationError("Công thức tính điểm của khóa huấn luyện chưa có thành phần điểm nào");
        }
        return formula;
    }

    private Map<String, ComponentScoreRequest> normalizeComponentScores(List<ComponentScoreRequest> componentScores) {
        if (componentScores == null || componentScores.isEmpty()) {
            throw commonValidationHelper.validationError("componentScores là bắt buộc");
        }
        Map<String, ComponentScoreRequest> byComponentId = new LinkedHashMap<>();
        for (ComponentScoreRequest componentScore : componentScores) {
            if (byComponentId.put(componentScore.getComponentId(), componentScore) != null) {
                throw commonValidationHelper.validationError("Trùng điểm thành phần cho componentId " + componentScore.getComponentId());
            }
        }
        return byComponentId;
    }

    private void ensureSuggestedCertificateExists(CourseParticipation participation, String actor) {
        if (participation.getLeader() == null || participation.getCourse() == null) {
            return;
        }
        if (certificateRepository.findByLeaderIdAndCourseCode(participation.getLeader().getId(), participation.getCourse().getCourseCode()).isPresent()) {
            return;
        }
        Certificate certificate = new Certificate();
        certificate.setCertificateCode(nextCertificateCode(participation));
        certificate.setCertificateName(participation.getCourse().getCourseName());
        certificate.setLeader(participation.getLeader());
        certificate.setCourse(participation.getCourse());
        certificate.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        certificate.setSystemSuggested(Boolean.TRUE);
        certificate.setApprovedByException(Boolean.FALSE);
        certificate.setStatus(Boolean.TRUE);
        certificate.setCreatedBy(actor);
        certificate.setUpdatedBy(actor);
        certificateRepository.save(certificate);
    }

    private String nextCertificateCode(CourseParticipation participation) {
        String unitName = unitNameForCertificate(participation.getCourse());
        String prefix = certificateCodeGenerator.buildPrefix(unitName, participation.getCourse().getCourseType(), participation.getCourse().getHostYear());
        certificateRepository.lockCodePrefix(prefix);
        long nextSequence = certificateRepository.findHighestSequenceByCodePrefix(prefix).orElse(0L) + 1L;
        return certificateCodeGenerator.buildCode(unitName, participation.getCourse().getCourseType(), participation.getCourse().getHostYear(), nextSequence);
    }

    private String unitNameForCertificate(TrainingCourse course) {
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            if (course.getDeanery() == null || course.getDeanery().getName() == null) {
                throw commonValidationHelper.validationError("Khóa huấn luyện cấp giáo hạt phải xác định được tên giáo hạt để tạo mã chứng chỉ");
            }
            return course.getDeanery().getName();
        }
        if (course.getDiocese() == null || course.getDiocese().getName() == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện cấp giáo phận phải xác định được tên giáo phận để tạo mã chứng chỉ");
        }
        return course.getDiocese().getName();
    }

    private TrainingCourse requireCourse(String courseCode) {
        return trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
    }

    private void requireRegistrationWindowOpen(TrainingCourse course) {
        OffsetDateTime now = OffsetDateTime.now();
        if (course.getRegistrationStartAt() == null || course.getRegistrationEndAt() == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa cấu hình thời gian nhận đăng ký");
        }
        if (now.isBefore(course.getRegistrationStartAt())) {
            throw commonValidationHelper.validationError("Chưa đến thời gian nhận đăng ký");
        }
        if (now.isAfter(course.getRegistrationEndAt())) {
            throw commonValidationHelper.validationError("Đã hết thời gian nhận đăng ký");
        }
    }

    private String resolveExceptionReason(Map<UUID, String> exceptionReasonsByLeaderId, UUID leaderId) {
        if (exceptionReasonsByLeaderId == null || exceptionReasonsByLeaderId.isEmpty()) {
            return null;
        }
        return exceptionReasonsByLeaderId.get(leaderId);
    }

    private List<UUID> normalizeLeaderIds(List<UUID> leaderIds) {
        if (leaderIds == null || leaderIds.isEmpty()) {
            throw commonValidationHelper.validationError("leaderIds là bắt buộc");
        }
        return new ArrayList<>(new LinkedHashSet<>(leaderIds));
    }

    private String submitNotificationPermission(TrainingCourse course) {
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            return CourseParticipationAccessEvaluator.APPROVE_DEANERY;
        }
        return CourseParticipationAccessEvaluator.SUBMIT_DEANERY;
    }

    private int previewParishSubmissionAutoApprovedCount(TrainingCourse course, List<CourseParticipation> participations) {
        if (!trainingWorkflowSupport.shouldAutoApproveAfterParishSubmission(course.getCourseType())) {
            return 0;
        }
        return (int) participations.stream().filter(participation -> Boolean.TRUE.equals(participation.getEligible())).count();
    }

    private int previewDeanerySubmissionAutoApprovedCount(TrainingCourse course, List<CourseParticipation> participations) {
        if (!trainingWorkflowSupport.shouldAutoApproveAfterDeanerySubmission(course.getCourseType())) {
            return 0;
        }
        return (int) participations.stream().filter(participation -> Boolean.TRUE.equals(participation.getEligible())).count();
    }

    private TrainingRegistrationBatchResponse buildBatchResponse(
            TrainingCourse course,
            UnitType senderUnitType,
            UUID senderUnitId,
            String senderUnitName,
            List<CourseParticipation> participations,
            int autoApprovedCount
    ) {
        int total = participations.size();
        int pendingCount = Math.max(total - autoApprovedCount, 0);
        return TrainingRegistrationBatchResponse.builder()
                .courseCode(course.getCourseCode())
                .courseType(course.getCourseType())
                .senderUnitType(senderUnitType)
                .senderUnitId(senderUnitId)
                .senderUnitName(senderUnitName)
                .totalCount(total)
                .autoApprovedCount(autoApprovedCount)
                .pendingCount(pendingCount)
                .participations(participations.stream().map(this::toResponse).toList())
                .build();
    }

    private TrainingNotificationCommand buildSubmissionNotification(
            String eventType,
            String senderUnitName,
            UnitType senderUnitType,
            UUID senderUnitId,
            String courseCode,
            int totalCount,
            String actor
    ) {
        return TrainingNotificationCommand.builder()
                .eventType(eventType)
                .title("Danh sách đăng ký mới")
                .message(senderUnitName + " gửi " + totalCount + " đăng ký cho khóa " + courseCode)
                .senderUnitType(senderUnitType)
                .senderUnitId(senderUnitId)
                .courseCode(courseCode)
                .totalCount(totalCount)
                .approvedCount(0)
                .pendingCount(totalCount)
                .actor(actor)
                .build();
    }

    private void publishBatchEvent(
            String eventType,
            TrainingCourse course,
            List<CourseParticipation> participations,
            UnitType senderUnitType,
            UUID senderUnitId,
            String senderUnitName,
            String actor,
            UnitType recipientScopeType,
            UUID recipientScopeId,
            String recipientPermission
    ) {
        publishEvent(eventType, "trainingCourse", course.getCourseCode(), actor, Map.of(
                "courseCode", course.getCourseCode(),
                "courseType", course.getCourseType().name(),
                "participationIds", participations.stream().map(CourseParticipation::getId).toList(),
                "senderUnitType", senderUnitType.name(),
                "senderUnitId", String.valueOf(senderUnitId),
                "senderUnitName", senderUnitName,
                "recipientScopeType", recipientScopeType.name(),
                "recipientScopeId", String.valueOf(recipientScopeId),
                "recipientPermission", recipientPermission,
                "totalCount", participations.size()
        ));
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
                .scoreFormulaId(participation.getCourse() == null || participation.getCourse().getScoreFormula() == null ? null : participation.getCourse().getScoreFormula().getId())
                .registrationDate(participation.getRegistrationDate())
                .participationStatus(participation.getParticipationStatus())
                .totalScore(participation.getTotalScore())
                .passingScore(participation.getCourse() == null || participation.getCourse().getScoreFormula() == null ? null : participation.getCourse().getScoreFormula().getPassingScore())
                .passed(participation.getCourse() != null
                        && participation.getCourse().getScoreFormula() != null
                        && participation.getTotalScore() != null
                        && participation.getTotalScore().compareTo(participation.getCourse().getScoreFormula().getPassingScore()) >= 0)
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
                .componentScores(participation.getComponentScores().stream()
                        .sorted(Comparator.comparing(score -> score.getComponent().getDisplayOrder(), Comparator.nullsLast(Integer::compareTo)))
                        .map(score -> ComponentScoreResponse.builder()
                                .id(score.getId())
                                .componentId(score.getComponent() == null ? null : score.getComponent().getId())
                                .componentCode(score.getComponent() == null ? null : score.getComponent().getCode())
                                .componentName(score.getComponent() == null ? null : score.getComponent().getName())
                                .maxScore(score.getComponent() == null ? null : score.getComponent().getMaxScore())
                                .score(score.getScore())
                                .note(score.getNote())
                                .displayOrder(score.getComponent() == null ? null : score.getComponent().getDisplayOrder())
                                .build())
                        .toList())
                .status(participation.getStatus())
                .build();
    }

    private void requirePendingManualApproval(WorkflowApprovalStatus status) {
        if (status != WorkflowApprovalStatus.PENDING && status != WorkflowApprovalStatus.SUBMITTED) {
            throw commonValidationHelper.conflict("Hồ sơ đã ở trạng thái kết thúc hoặc không chờ duyệt");
        }
    }

    private void requireApprovedForScoring(CourseParticipation participation) {
        if (participation.getParticipationStatus() == ParticipationStatus.CANCELLED) {
            throw commonValidationHelper.conflict("Không thể chấm điểm hồ sơ đã hủy");
        }
        WorkflowApprovalStatus requiredStatus = trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())
                ? participation.getDeaneryApprovalStatus() : participation.getDioceseApprovalStatus();
        if (requiredStatus != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.validationError("Chỉ được chấm điểm sau khi hồ sơ đã được duyệt đúng cấp");
        }
    }

    private BaseSearchRequest copySearchRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage()); copy.setSize(source.getSize()); copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus()); copy.setSortBy(source.getSortBy()); copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
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

    public static String autoApprovalActor() {
        return AUTO_APPROVAL_SYSTEM_ACTOR;
    }
}
