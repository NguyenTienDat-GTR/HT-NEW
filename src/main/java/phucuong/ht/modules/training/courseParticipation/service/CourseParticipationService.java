package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
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
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipationComponentScore;
import phucuong.ht.modules.training.courseParticipation.model.request.*;
import phucuong.ht.modules.training.courseParticipation.model.response.ComponentScoreResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.RegistrationCandidateResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.ScoreEntryComponentResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingBulkApprovalResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingRegistrationBatchResponse;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseStatusSupport;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;
import phucuong.ht.modules.training.scoreComponentTemplate.repository.ScoreComponentTemplateRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseParticipationService {

    private static final String AUTO_APPROVAL_SYSTEM_ACTOR = "SYSTEM_AUTO_APPROVAL";
    private static final String EVENT_BATCH_SUBMITTED = "training.registration.batch_submitted";

    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final ScoreComponentTemplateRepository scoreComponentTemplateRepository;
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
        } else if (hasAuthority(authentication, CourseParticipationAccessEvaluator.READ_PARISH)) {
            scoped.getFilters().put("leader.parish.id", accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.READ_PARISH));
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem hồ sơ đào tạo");
        }
        Page<CourseParticipation> page = courseParticipationRepository.findAll(scoped);
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    // Thành phần điểm áp dụng cho khóa (cùng loại khóa và đơn vị sở hữu) để nhập điểm theo cột.
    @Transactional(readOnly = true)
    public List<ScoreEntryComponentResponse> findScoreComponents(String courseCode) {
        TrainingCourse course = requireCourse(courseCode);
        if (course.getCourseType() == null) {
            return List.of();
        }
        return scoreComponentTemplateRepository.findActiveForCourse(
                        course.getCourseType().getTypeCode(),
                        course.getDiocese() == null ? null : course.getDiocese().getId(),
                        course.getDeanery() == null ? null : course.getDeanery().getId())
                .stream()
                .map(component -> ScoreEntryComponentResponse.builder()
                        .id(component.getId())
                        .code(component.getCode())
                        .name(component.getName())
                        .maxScore(component.getMaxScore())
                        .coefficient(component.getCoefficient())
                        .includeInFinal(component.getIncludeInFinal())
                        .displayOrder(component.getDisplayOrder())
                        .build())
                .toList();
    }

    // Danh sách tham dự: chỉ huynh trưởng đã được duyệt đăng ký (hồ sơ chờ duyệt/bị từ chối không nằm trong danh sách).
    @Transactional(readOnly = true)
    public Page<CourseParticipationResponse> findParticipants(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest approvedOnly = copySearchRequest(request);
        approvedOnly.getFilters().put("approvalStatus", WorkflowApprovalStatus.APPROVED.name());
        return findAll(approvedOnly, authentication);
    }

    @Transactional(readOnly = true)
    public CourseParticipationResponse findParticipantById(String participationId) {
        CourseParticipationResponse response = findById(participationId);
        if (response.getApprovalStatus() != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.notFound("Không tìm thấy huynh trưởng tham dự " + participationId);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public CourseParticipationResponse findById(String participationId) {
        return toResponse(courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ đào tạo: " + participationId)));
    }

    @Transactional
    public TrainingRegistrationBatchResponse register(String courseCode, TrainingRegistrationRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(courseCode);
        requireActiveRegistrationData(course);
        requireRegistrationWindowOpen(course);

        // Đơn vị gửi: giáo xứ (khóa giáo phận/giáo hạt trong phạm vi), giáo phận (chỉ khóa giáo phận) hoặc giáo hạt (chỉ khóa giáo hạt).
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course.getCourseType());
        UnitType senderType;
        UUID senderId;
        String senderName;
        senderType = resolveSenderType(authentication);
        if (senderType == UnitType.PARISH) {
            senderId = accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
            Parish parish = parishRepository.findByIdWithDeaneryAndDiocese(senderId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ " + senderId));
            requireParishWithinCourseScope(course, parish);
            senderName = parish.getName();
        } else if (senderType == UnitType.DIOCESE) {
            senderId = accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE);
            if (deaneryManaged || course.getDiocese() == null || !senderId.equals(course.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Giáo phận chỉ được gửi đăng ký cho khóa thuộc giáo phận của mình");
            }
            senderName = course.getDiocese().getName();
        } else {
            senderId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
            if (!deaneryManaged || course.getDeanery() == null || !senderId.equals(course.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Giáo hạt chỉ được gửi đăng ký cho khóa thuộc giáo hạt của mình");
            }
            senderName = course.getDeanery().getName();
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gửi danh sách");
        OffsetDateTime submittedAt = OffsetDateTime.now();

        List<CourseParticipation> createdParticipations = new ArrayList<>();
        Map<UUID, String> exceptionReasons = normalizeExceptionReasons(request.getExceptionReasonsByLeaderId());
        List<UUID> registrationLeaderIds = normalizeLeaderIds(request.getLeaderIds(), exceptionReasons);
        for (UUID leaderId : registrationLeaderIds) {
            Leader leader = leaderRepository.findByIdWithParish(leaderId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy leader " + leaderId));
            if (!Boolean.TRUE.equals(leader.getStatus())) {
                throw commonValidationHelper.validationError("Chỉ được đăng ký Huynh Trưởng đang hoạt động");
            }
            requireLeaderWithinSender(leader, senderType, senderId);
            if (senderType != UnitType.PARISH) {
                requireParishWithinCourseScope(course, leader.getParish());
            }

            if (courseParticipationRepository.existsByLeaderIdAndCourseCode(leaderId, courseCode)) {
                throw commonValidationHelper.conflict("Huynh Trưởng đã có hồ sơ tham gia khóa huấn luyện này");
            }
            boolean eligible = isEligible(leader, course);
            boolean exception = exceptionReasons.containsKey(leaderId);
            if (!exception && !eligible) {
                throw commonValidationHelper.validationError("Huynh Trưởng " + buildFullName(leader) + " không đủ điều kiện, hãy đưa vào danh sách ngoại lệ kèm lí do");
            }
            if (exception && eligible) {
                throw commonValidationHelper.validationError("Huynh Trưởng " + buildFullName(leader) + " đã đủ điều kiện, hãy đưa vào danh sách đăng ký thay vì danh sách ngoại lệ");
            }
            if (exception && exceedsNearestAchievedLevel(leader.getLeaderLevel(), course)) {
                throw commonValidationHelper.validationError("Huynh Trưởng " + buildFullName(leader) + " có cấp độ cao hơn cấp độ đạt được gần nhất của loại khóa nên không thể đưa vào danh sách ngoại lệ");
            }
            String exceptionReason = exception ? exceptionReasons.get(leaderId) : null;

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
            participation.setCreatedAt(submittedAt);
            participation.setUpdatedAt(submittedAt);
            participation.setCreatedBy(actor);
            participation.setUpdatedBy(actor);
            participation.setStatus(Boolean.TRUE);
            participation.setApprovalStatus(WorkflowApprovalStatus.PENDING);
            courseParticipationRepository.save(participation);
            createdParticipations.add(participation);
        }

        // Người nhận: tài khoản duyệt của đơn vị sở hữu khóa (giáo hạt với khóa giáo hạt, giáo phận với khóa giáo phận).
        UnitType recipientScopeType = deaneryManaged ? UnitType.DEANERY : UnitType.DIOCESE;
        UUID recipientScopeId = deaneryManaged ? course.getDeanery().getId() : course.getDiocese().getId();
        String recipientPermission = deaneryManaged
                ? CourseParticipationAccessEvaluator.APPROVE_DEANERY
                : CourseParticipationAccessEvaluator.APPROVE_DIOCESE;
        List<String> recipients = resolveCourseRecipients(course, deaneryManaged, recipientPermission, recipientScopeType, recipientScopeId);
        trainingNotificationService.notifyUsers(recipients, buildSubmissionNotification(
                "training.registration.submitted",
                senderName,
                senderType,
                senderId,
                courseCode,
                createdParticipations.size(),
                actor
        ));

        publishBatchEvent(
                EVENT_BATCH_SUBMITTED,
                course,
                createdParticipations,
                senderType,
                senderId,
                senderName,
                actor,
                recipientScopeType,
                recipientScopeId,
                recipientPermission
        );

        return buildBatchResponse(course, senderType, senderId, senderName, createdParticipations, previewParishSubmissionAutoApprovedCount(course, createdParticipations));
    }

    // Danh sách huynh trưởng trong phạm vi tài khoản để lập danh sách đăng ký (eligible=true) hoặc ngoại lệ (eligible=false).
    @Transactional(readOnly = true)
    public Page<RegistrationCandidateResponse> findRegistrationCandidates(
            String courseCode,
            boolean eligibleOnly,
            String search,
            UUID deaneryId,
            UUID parishId,
            List<UUID> excludeLeaderIds,
            int page,
            int size,
            Authentication authentication
    ) {
        TrainingCourse course = requireCourse(courseCode);
        // Phạm vi theo tài khoản: giáo xứ -> chỉ giáo xứ đó; giáo phận -> toàn giáo phận; giáo hạt -> các giáo xứ trong giáo hạt.
        UUID scopeDioceseId = null;
        UUID scopeDeaneryId = null;
        UUID scopeParishId = null;
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course.getCourseType());
        UnitType senderType = resolveSenderType(authentication);
        if (senderType == UnitType.PARISH) {
            scopeParishId = accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
        } else if (senderType == UnitType.DIOCESE) {
            scopeDioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE);
            if (deaneryManaged || course.getDiocese() == null || !scopeDioceseId.equals(course.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Giáo phận chỉ được lập danh sách cho khóa thuộc giáo phận của mình");
            }
        } else {
            scopeDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
            if (!deaneryManaged || course.getDeanery() == null || !scopeDeaneryId.equals(course.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Giáo hạt chỉ được lập danh sách cho khóa thuộc giáo hạt của mình");
            }
        }
        if (scopeParishId != null && parishId != null && !parishId.equals(scopeParishId)) {
            return new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(page, size), 0);
        }
        if (scopeDeaneryId != null && deaneryId != null && !deaneryId.equals(scopeDeaneryId)) {
            return new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(page, size), 0);
        }
        UUID queryParishId = scopeParishId != null ? scopeParishId : parishId;
        UUID queryDeaneryId = scopeDeaneryId != null ? scopeDeaneryId : deaneryId;

        Set<UUID> excluded = excludeLeaderIds == null ? Set.of() : new HashSet<>(excludeLeaderIds);
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<RegistrationCandidateResponse> matched = leaderRepository.findActiveInScope(scopeDioceseId, queryDeaneryId, queryParishId).stream()
                .filter(leader -> !excluded.contains(leader.getId()))
                .filter(leader -> matchesKeyword(leader, keyword))
                .filter(leader -> !courseParticipationRepository.existsByLeaderIdAndCourseCode(leader.getId(), courseCode))
                .map(leader -> toCandidateResponse(leader, isEligible(leader, course)))
                .filter(candidate -> candidate.getEligible() == eligibleOnly)
                .filter(candidate -> eligibleOnly || !exceedsNearestAchievedLevel(candidate.getLeaderLevel(), course))
                .toList();

        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageImpl<>(matched.subList(from, to), org.springframework.data.domain.PageRequest.of(page, size), matched.size());
    }

    // Cấp độ đạt được gần nhất của loại khóa: cấp thăng của khóa tiên quyết (nếu không có thì cấp hiện tại yêu cầu).
    // Huynh trưởng có cấp cao hơn mức này không được đưa vào danh sách ngoại lệ.
    private boolean exceedsNearestAchievedLevel(LeaderLevel leaderLevel, TrainingCourse course) {
        TrainingCourseType courseType = course.getCourseType();
        if (courseType == null || leaderLevel == null) {
            return false;
        }
        LeaderLevel nearest = trainingWorkflowSupport.prerequisiteOf(courseType)
                .map(TrainingCourseType::getResultingLevel)
                .orElse(courseType.getRequiredCurrentLevel());
        return nearest != null && leaderLevel.ordinal() > nearest.ordinal();
    }

    private UnitType resolveSenderType(Authentication authentication) {
        UnitType type = trainingWorkflowSupport.resolveRegistrationSender(
                hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH),
                hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE),
                hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY),
                accountScopeValidationHelper.hasDirectDiocese(authentication),
                accountScopeValidationHelper.hasDirectDeanery(authentication)
        );
        if (type == null) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gửi đăng ký khóa huấn luyện");
        }
        return type;
    }

    private boolean matchesKeyword(Leader leader, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        String haystack = String.join(" ",
                Objects.toString(buildFullName(leader), ""),
                Objects.toString(leader.getHolyName(), ""),
                leader.getParish() == null ? "" : Objects.toString(leader.getParish().getName(), ""),
                leader.getParish() == null || leader.getParish().getDeanery() == null ? "" : Objects.toString(leader.getParish().getDeanery().getName(), "")
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    private RegistrationCandidateResponse toCandidateResponse(Leader leader, boolean eligible) {
        Parish parish = leader.getParish();
        Deanery deanery = parish == null ? null : parish.getDeanery();
        return RegistrationCandidateResponse.builder()
                .id(leader.getId())
                .holyName(leader.getHolyName())
                .fullName(buildFullName(leader))
                .birthDate(leader.getBirthDate())
                .phoneNumber(leader.getPhoneNumber())
                .email(leader.getEmail())
                .leaderLevel(leader.getLeaderLevel())
                .parishId(parish == null ? null : parish.getId())
                .parishName(parish == null ? null : parish.getName())
                .deaneryId(deanery == null ? null : deanery.getId())
                .deaneryName(deanery == null ? null : deanery.getName())
                .eligible(eligible)
                .build();
    }

    @Transactional
    public CourseParticipationResponse deaneryApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        if (!trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
            throw commonValidationHelper.validationError("Chỉ khóa Dự trưởng chỉ cần giáo hạt duyệt danh sách");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo hạt");
        OffsetDateTime now = OffsetDateTime.now();
        requirePendingManualApproval(participation.getApprovalStatus());
        applyApprovalDecision(participation, Boolean.TRUE.equals(request.getApproved()));
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.deanery_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        return toResponse(participation);
    }

    @Transactional
    public CourseParticipationResponse dioceseApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
            throw commonValidationHelper.validationError("Khóa Dự trưởng không không cần giáo phận duyệt danh sách");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo phận");
        OffsetDateTime now = OffsetDateTime.now();
        requirePendingManualApproval(participation.getApprovalStatus());
        applyApprovalDecision(participation, Boolean.TRUE.equals(request.getApproved()));
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.diocese_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        return toResponse(participation);
    }

    @Transactional
    public CourseParticipationResponse score(String participationId, TrainingScoreRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người nhập điểm");
        OffsetDateTime now = OffsetDateTime.now();

        if (participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Không thể sửa điểm sau khi chứng chỉ đã được duyệt");
        }
        requireApprovedForScoring(participation);
        List<ScoreComponentTemplate> components = requireScoreComponents(participation);
        Map<String, ComponentScoreRequest> requestedScores = normalizeComponentScores(request.getComponentScores());

        participation.getComponentScores().clear();
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal coefficientSum = BigDecimal.ZERO;
        for (ScoreComponentTemplate component : components) {
            ComponentScoreRequest componentRequest = requestedScores.remove(component.getId());
            if (componentRequest == null) {
                throw commonValidationHelper.validationError("Thiếu điểm cho thành phần bắt buộc " + component.getName());
            }
            if (componentRequest.getScore().compareTo(component.getMaxScore()) > 0) {
                throw commonValidationHelper.validationError("Điểm vượt quá điểm tối đa của thành phần " + component.getName());
            }

            CourseParticipationComponentScore persistedScore = new CourseParticipationComponentScore();
            persistedScore.setParticipation(participation);
            persistedScore.setComponent(component);
            persistedScore.setScore(componentRequest.getScore());
            persistedScore.setNote(commonValidationHelper.normalizeOptionalText(componentRequest.getNote()));
            persistedScore.setStatus(Boolean.TRUE);
            persistedScore.setCreatedAt(now);
            persistedScore.setUpdatedAt(now);
            persistedScore.setCreatedBy(actor);
            persistedScore.setUpdatedBy(actor);
            participation.getComponentScores().add(persistedScore);
            if (Boolean.TRUE.equals(component.getIncludeInFinal())) {
                weightedSum = weightedSum.add(componentRequest.getScore().multiply(component.getCoefficient()));
                coefficientSum = coefficientSum.add(component.getCoefficient());
            }
        }
        if (!requestedScores.isEmpty()) {
            throw commonValidationHelper.validationError("Điểm thành phần gửi lên có thành phần không thuộc loại khóa của khóa học");
        }
        if (coefficientSum.signum() <= 0) {
            throw commonValidationHelper.validationError("Loại khóa chưa có thành phần điểm nào được tính vào điểm tổng kết");
        }

        // Điểm tổng kết = trung bình có hệ số của các thành phần có includeInFinal = true.
        BigDecimal totalScore = weightedSum.divide(coefficientSum, 2, java.math.RoundingMode.HALF_UP);
        participation.setTotalScore(totalScore);
        participation.setReview(request.getReview());
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        if (totalScore.compareTo(participation.getCourse().getPassingScore()) >= 0) {
            participation.setResult(CourseResult.COMPLETED);
            participation.setParticipationStatus(ParticipationStatus.COMPLETED);
            participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
            if (participation.getCertificateRequestedAt() == null) {
                participation.setCertificateRequestedAt(now);
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

    private List<ScoreComponentTemplate> requireScoreComponents(CourseParticipation participation) {
        if (participation.getCourse() == null || participation.getCourse().getCourseType() == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa có loại khóa");
        }
        var course = participation.getCourse();
        List<ScoreComponentTemplate> components = scoreComponentTemplateRepository.findActiveForCourse(
                course.getCourseType().getTypeCode(),
                course.getDiocese() == null ? null : course.getDiocese().getId(),
                course.getDeanery() == null ? null : course.getDeanery().getId());
        if (components.isEmpty()) {
            throw commonValidationHelper.validationError("Loại khóa của khóa huấn luyện chưa có thành phần điểm nào");
        }
        return components;
    }

    private Map<String, ComponentScoreRequest> normalizeComponentScores(List<ComponentScoreRequest> componentScores) {
        if (componentScores == null || componentScores.isEmpty()) {
            throw commonValidationHelper.validationError("Điểm thành phần là bắt buộc");
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
        OffsetDateTime now = OffsetDateTime.now();
        Certificate certificate = new Certificate();
        certificate.setCertificateCode(nextCertificateCode(participation));
        certificate.setCertificateName(participation.getCourse().getCourseName());
        certificate.setLeader(participation.getLeader());
        certificate.setCourse(participation.getCourse());
        certificate.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        certificate.setSystemSuggested(Boolean.TRUE);
        certificate.setApprovedByException(Boolean.FALSE);
        certificate.setStatus(Boolean.TRUE);
        certificate.setCreatedAt(now);
        certificate.setUpdatedAt(now);
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
        return trainingCourseRepository.findByCodeWithType(courseCode)
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
        if (TrainingCourseStatusSupport.registrationStatus(course, now) == CourseRegistrationStatus.CLOSED) {
            throw commonValidationHelper.validationError("Khóa huấn luyện đang đóng đăng ký");
        }
    }

    private void requireActiveRegistrationData(TrainingCourse course) {
        if (!Boolean.TRUE.equals(course.getStatus())) {
            throw commonValidationHelper.validationError("Khóa huấn luyện đã ngừng hoạt động");
        }
        if (course.getCourseType() != null && !Boolean.TRUE.equals(course.getCourseType().getStatus())) {
            throw commonValidationHelper.validationError("Loại khóa huấn luyện đã ngừng hoạt động");
        }
    }

    private void requireParishWithinCourseScope(TrainingCourse course, Parish parish) {
        if (parish == null || parish.getDeanery() == null) {
            throw commonValidationHelper.forbidden("Giáo xứ hiện tại không có phạm vi giáo hạt hợp lệ để gửi đăng ký");
        }
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            if (course.getDeanery() == null || course.getDeanery().getId() == null
                    || !course.getDeanery().getId().equals(parish.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Giáo xứ hiện tại không thuộc giáo hạt sở hữu khóa dự trưởng");
            }
            return;
        }
        if (course.getDiocese() == null || course.getDiocese().getId() == null
                || parish.getDeanery().getDiocese() == null
                || !course.getDiocese().getId().equals(parish.getDeanery().getDiocese().getId())) {
            throw commonValidationHelper.forbidden("Giáo xứ hiện tại không thuộc giáo phận sở hữu khóa huấn luyện");
        }
    }

    private void requireLeaderWithinSender(Leader leader, UnitType senderType, UUID senderId) {
        Parish parish = leader.getParish();
        UUID actualId = null;
        if (parish != null) {
            switch (senderType) {
                case PARISH -> actualId = parish.getId();
                case DEANERY -> actualId = parish.getDeanery() == null ? null : parish.getDeanery().getId();
                default -> actualId = parish.getDeanery() == null || parish.getDeanery().getDiocese() == null
                        ? null : parish.getDeanery().getDiocese().getId();
            }
        }
        if (actualId == null || !actualId.equals(senderId)) {
            throw commonValidationHelper.forbidden("Không thể đăng ký Huynh Trưởng ngoài phạm vi đơn vị hiện tại");
        }
    }

    private Map<UUID, String> normalizeExceptionReasons(Map<UUID, String> exceptionReasonsByLeaderId) {
        Map<UUID, String> normalized = new LinkedHashMap<>();
        if (exceptionReasonsByLeaderId == null) {
            return normalized;
        }
        exceptionReasonsByLeaderId.forEach((leaderId, reason) -> {
            if (leaderId == null) {
                return;
            }
            if (reason == null || reason.isBlank()) {
                throw commonValidationHelper.validationError("Danh sách ngoại lệ phải có lí do cho từng huynh trưởng");
            }
            normalized.put(leaderId, reason.trim());
        });
        return normalized;
    }

    // Gộp danh sách đăng ký + danh sách ngoại lệ; hai danh sách không được trùng người.
    private List<UUID> normalizeLeaderIds(List<UUID> leaderIds, Map<UUID, String> exceptionReasons) {
        Set<UUID> standard = leaderIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(leaderIds);
        standard.remove(null);
        if (standard.isEmpty() && exceptionReasons.isEmpty()) {
            throw commonValidationHelper.validationError("Cần có ít nhất một huynh trưởng trong danh sách đăng ký hoặc danh sách ngoại lệ");
        }
        if (standard.stream().anyMatch(exceptionReasons::containsKey)) {
            throw commonValidationHelper.validationError("Danh sách đăng ký và danh sách ngoại lệ không được trùng huynh trưởng");
        }
        List<UUID> all = new ArrayList<>(standard);
        all.addAll(exceptionReasons.keySet());
        return all;
    }

    // Người nhận lấy từ danh sách tài khoản đã chọn khi tạo khóa (nếu còn hợp lệ), ngược lại gửi cho mọi tài khoản đủ quyền.
    private List<String> resolveCourseRecipients(TrainingCourse course, boolean useCourseList, String permissionCode, UnitType scopeType, UUID scopeId) {
        List<String> eligible = notificationRecipientResolver.resolveRecipients(null, permissionCode, scopeType, scopeId);
        if (!useCourseList || course.getRecipientUsernames().isEmpty()) {
            return eligible;
        }
        List<String> chosen = eligible.stream().filter(course.getRecipientUsernames()::contains).toList();
        return chosen.isEmpty() ? eligible : chosen;
    }

    private int previewParishSubmissionAutoApprovedCount(TrainingCourse course, List<CourseParticipation> participations) {
        if (!trainingWorkflowSupport.shouldAutoApproveAfterSubmission(course.getCourseType())) {
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
                .courseTypeCode(course.getCourseType() == null ? null : course.getCourseType().getTypeCode())
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
        publishEvent(eventType, "trainingCourse", course.getCourseCode(), actor, Map.ofEntries(
                Map.entry("courseCode", course.getCourseCode()),
                Map.entry("courseType", course.getCourseType().getTypeCode()),
                Map.entry("deaneryManaged", trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())),
                Map.entry("participationIds", participations.stream().map(CourseParticipation::getId).toList()),
                Map.entry("senderUnitType", senderUnitType.name()),
                Map.entry("senderUnitId", String.valueOf(senderUnitId)),
                Map.entry("senderUnitName", senderUnitName),
                Map.entry("recipientScopeType", recipientScopeType.name()),
                Map.entry("recipientScopeId", String.valueOf(recipientScopeId)),
                Map.entry("recipientPermission", recipientPermission),
                Map.entry("totalCount", participations.size())
        ));
    }

    private boolean isEligible(Leader leader, TrainingCourse course) {
        TrainingCourseType courseType = course.getCourseType();
        if (courseType != null) {
            if (courseType.getRequiredCurrentLevel() != null && leader.getLeaderLevel() != courseType.getRequiredCurrentLevel()) {
                return false;
            }
            if (courseType.getRequiredMinAge() != null) {
                if (leader.getBirthDate() == null) {
                    return false;
                }
                int age = Period.between(leader.getBirthDate(), LocalDate.now()).getYears();
                if (age < courseType.getRequiredMinAge()) {
                    return false;
                }
            }
        }

        return trainingWorkflowSupport.prerequisiteOf(course.getCourseType())
                .map(prerequisite -> meetsPrerequisite(leader, course, prerequisite))
                .orElse(true);
    }

    // Phải đạt khóa tiên quyết, và năm tổ chức khóa này - năm đạt khóa tiên quyết >= số năm hoạt động của khóa.
    private boolean meetsPrerequisite(Leader leader, TrainingCourse course, TrainingCourseType prerequisite) {
        Optional<Integer> achievedYear = courseParticipationRepository
                .findEarliestCompletedYearByLeaderAndCourseType(leader.getId(), prerequisite.getTypeCode());
        if (achievedYear.isEmpty()) {
            return false;
        }
        int requiredYears = course.getYearsActive() == null ? 0 : course.getYearsActive();
        return resolveHostYear(course) - achievedYear.get() >= requiredYears;
    }

    private int resolveHostYear(TrainingCourse course) {
        if (course.getHostYear() != null) {
            return course.getHostYear();
        }
        if (course.getStartDate() != null) {
            return course.getStartDate().getYear();
        }
        return LocalDate.now().getYear();
    }

    private CourseParticipationResponse toResponse(CourseParticipation participation) {
        return CourseParticipationResponse.builder()
                .id(participation.getId())
                .leaderId(participation.getLeader() == null ? null : participation.getLeader().getId())
                .leaderFullName(buildFullName(participation.getLeader()))
                .courseCode(participation.getCourse() == null ? null : participation.getCourse().getCourseCode())
                .courseName(participation.getCourse() == null ? null : participation.getCourse().getCourseName())
                .courseTypeCode(participation.getCourse() == null || participation.getCourse().getCourseType() == null ? null : participation.getCourse().getCourseType().getTypeCode())
                .registrationDate(participation.getRegistrationDate())
                .participationStatus(participation.getParticipationStatus())
                .totalScore(participation.getTotalScore())
                .passingScore(participation.getCourse() == null ? null : participation.getCourse().getPassingScore())
                .passed(participation.getCourse() != null
                        && participation.getTotalScore() != null
                        && participation.getTotalScore().compareTo(participation.getCourse().getPassingScore()) >= 0)
                .result(participation.getResult())
                .review(participation.getReview())
                .eligible(participation.getEligible())
                .exceptionReason(participation.getExceptionReason())
                .parishSubmittedAt(participation.getParishSubmittedAt())
                .approvalStatus(participation.getApprovalStatus())
                .createdBy(participation.getCreatedBy())
                .parishId(participation.getLeader() == null || participation.getLeader().getParish() == null ? null : participation.getLeader().getParish().getId())
                .parishName(participation.getLeader() == null || participation.getLeader().getParish() == null ? null : participation.getLeader().getParish().getName())
                .deaneryId(participation.getLeader() == null || participation.getLeader().getParish() == null || participation.getLeader().getParish().getDeanery() == null ? null : participation.getLeader().getParish().getDeanery().getId())
                .deaneryName(participation.getLeader() == null || participation.getLeader().getParish() == null || participation.getLeader().getParish().getDeanery() == null ? null : participation.getLeader().getParish().getDeanery().getName())
                .managementUnitType(participation.getCourse() == null || participation.getCourse().getCourseType() == null ? null : participation.getCourse().getCourseType().getManagementUnitType())
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
                                .coefficient(score.getComponent() == null ? null : score.getComponent().getCoefficient())
                                .includeInFinal(score.getComponent() == null ? null : score.getComponent().getIncludeInFinal())
                                .score(score.getScore())
                                .note(score.getNote())
                                .displayOrder(score.getComponent() == null ? null : score.getComponent().getDisplayOrder())
                                .build())
                        .toList())
                .status(participation.getStatus())
                .build();
    }

    private String buildFullName(phucuong.ht.modules.leader.model.entity.Leader leader) {
        if (leader == null) {
            return null;
        }
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }

    // Thu hồi đăng ký: chỉ người gửi đăng ký, và chỉ khi hồ sơ còn chờ duyệt. Hồ sơ bị xóa để huynh trưởng có thể được đăng ký lại.
    @Transactional
    public void withdraw(String participationId, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi đăng ký");
        if (!actor.equals(participation.getCreatedBy())) {
            throw commonValidationHelper.forbidden("Chỉ người gửi đăng ký mới được thu hồi đăng ký");
        }
        // Chỉ thu hồi được khi hồ sơ chưa được duyệt hoặc từ chối.
        WorkflowApprovalStatus approval = participation.getApprovalStatus();
        if (approval != WorkflowApprovalStatus.PENDING && approval != WorkflowApprovalStatus.SUBMITTED) {
            throw commonValidationHelper.conflict("Không thể thu hồi đăng ký đã được duyệt hoặc từ chối");
        }
        String courseCode = participation.getCourse().getCourseCode();
        UUID leaderId = participation.getLeader().getId();
        courseParticipationRepository.delete(participation);
        publishEvent("training.registration.withdrawn", "courseParticipation", participationId, actor,
                Map.of("courseCode", courseCode, "leaderId", String.valueOf(leaderId)));
    }

    // Xóa hồ sơ khỏi danh sách tham dự: người có quyền duyệt, bất kể đã có điểm hay chưa, trừ hồ sơ đã có chứng nhận.
    @Transactional
    public void removeParticipant(String participationId, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ tham dự " + participationId));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người xóa hồ sơ");
        if (!canApproveCourseOf(participation, authentication)) {
            throw commonValidationHelper.forbidden("Không có quyền xóa hồ sơ khỏi danh sách tham dự");
        }
        UUID leaderId = participation.getLeader().getId();
        String courseCode = participation.getCourse().getCourseCode();
        Optional<Certificate> certificate = certificateRepository.findByLeaderIdAndCourseCode(leaderId, courseCode);
        boolean hasCertificate = participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.APPROVED
                || certificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.APPROVED).orElse(false);
        if (hasCertificate) {
            throw commonValidationHelper.conflict("Không thể xóa hồ sơ đã có chứng nhận");
        }
        // Chứng nhận hệ thống đề xuất (chưa duyệt) sinh ra khi chấm điểm đạt cũng bị xóa theo hồ sơ.
        certificate.ifPresent(certificateRepository::delete);
        courseParticipationRepository.delete(participation);
        publishEvent("training.participant.removed", "courseParticipation", participationId, actor,
                Map.of("courseCode", courseCode, "leaderId", String.valueOf(leaderId)));
    }

    // Duyệt hoặc từ chối nhiều hồ sơ một lần; hồ sơ không hợp lệ được bỏ qua và trả về kèm lý do.
    @Transactional
    public TrainingBulkApprovalResponse bulkApprove(TrainingBulkApprovalRequest request, Authentication authentication) {
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt");
        OffsetDateTime now = OffsetDateTime.now();
        List<TrainingBulkApprovalResponse.Failure> failures = new ArrayList<>();
        int success = 0;
        for (String id : new LinkedHashSet<>(request.getIds())) {
            Optional<CourseParticipation> found = courseParticipationRepository.findByIdWithLeaderAndCourse(id);
            if (found.isEmpty()) {
                failures.add(bulkFailure(id, null, "Không tìm thấy đăng ký"));
                continue;
            }
            CourseParticipation participation = found.get();
            String leaderName = buildFullName(participation.getLeader());
            String problem = bulkApprovalProblem(participation, authentication);
            if (problem != null) {
                failures.add(bulkFailure(id, leaderName, problem));
                continue;
            }
            applyApprovalDecision(participation, approved);
            participation.setReview(request.getReview());
            participation.setApprovedBy(actor);
            participation.setUpdatedAt(now);
            participation.setUpdatedBy(actor);
            boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType());
            publishEvent(deaneryManaged ? "training.registration.deanery_approved" : "training.registration.diocese_approved",
                    "courseParticipation", id, actor, Map.of("approved", approved));
            success++;
        }
        return TrainingBulkApprovalResponse.builder()
                .successCount(success)
                .failedCount(failures.size())
                .failures(failures)
                .build();
    }

    private TrainingBulkApprovalResponse.Failure bulkFailure(String id, String leaderName, String reason) {
        return TrainingBulkApprovalResponse.Failure.builder().id(id).leaderFullName(leaderName).reason(reason).build();
    }

    // Quyền duyệt theo cấp quản lý của khóa (giống endpoint duyệt từng hồ sơ) và hồ sơ phải đang chờ duyệt.
    private String bulkApprovalProblem(CourseParticipation participation, Authentication authentication) {
        if (!canApproveCourseOf(participation, authentication)) {
            return "Không có quyền duyệt hồ sơ này";
        }
        WorkflowApprovalStatus status = participation.getApprovalStatus();
        if (status != WorkflowApprovalStatus.PENDING && status != WorkflowApprovalStatus.SUBMITTED) {
            return "Hồ sơ đã ở trạng thái kết thúc hoặc không chờ duyệt";
        }
        return null;
    }

    // Tài khoản có quyền duyệt đúng cấp quản lý của khóa: giáo hạt với khóa giáo hạt, giáo phận với khóa giáo phận.
    private boolean canApproveCourseOf(CourseParticipation participation, Authentication authentication) {
        TrainingCourse course = participation.getCourse();
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            return hasAuthority(authentication, CourseParticipationAccessEvaluator.APPROVE_DEANERY)
                    && accountScopeValidationHelper.hasDirectDeanery(authentication)
                    && course.getDeanery() != null
                    && course.getDeanery().getId().equals(accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.APPROVE_DEANERY));
        }
        return hasAuthority(authentication, CourseParticipationAccessEvaluator.APPROVE_DIOCESE)
                && accountScopeValidationHelper.hasDirectDiocese(authentication)
                && course.getDiocese() != null
                && course.getDiocese().getId().equals(accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseParticipationAccessEvaluator.APPROVE_DIOCESE));
    }

    // Duyệt xong -> đã tham dự (ATTENDED); từ chối duyệt -> bị từ chối (REJECTED).
    private void applyApprovalDecision(CourseParticipation participation, boolean approved) {
        participation.setApprovalStatus(approved ? WorkflowApprovalStatus.APPROVED : WorkflowApprovalStatus.REJECTED);
        participation.setParticipationStatus(approved ? ParticipationStatus.ATTENDED : ParticipationStatus.REJECTED);
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
        WorkflowApprovalStatus requiredStatus = participation.getApprovalStatus();
        if (requiredStatus != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.validationError("Chỉ được chấm điểm sau khi hồ sơ đã được duyệt đúng cấp");
        }
    }

    private BaseSearchRequest copySearchRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
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
