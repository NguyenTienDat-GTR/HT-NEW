package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.repository.SearchRequests;
import phucuong.ht.common.utils.LeaderNames;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.security.AccountScopeResolver;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.certificate.CertificateCodeGenerator;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService.LevelOrder;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.ApprovalTransitionPolicy;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipationComponentScore;
import phucuong.ht.modules.training.courseParticipation.model.request.*;
import phucuong.ht.modules.training.courseParticipation.model.response.*;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent;
import phucuong.ht.modules.training.courseScoreComponent.repository.TrainingCourseScoreComponentRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.*;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseStatusSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseParticipationService {

    private static final String AUTO_APPROVAL_SYSTEM_ACTOR = "SYSTEM_AUTO_APPROVAL";
    private static final String REMOVED_FROM_PARTICIPANTS_REVIEW = "Bị xóa khỏi danh sách tham dự";

    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final TrainingCourseScoreComponentRepository courseScoreComponentRepository;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final AccountScopeResolver accountScopeResolver;
    private final phucuong.ht.modules.training.RegistrationSenderResolver registrationSenderResolver;
    private final CommonValidationHelper commonValidationHelper;
    private final CertificateRepository certificateRepository;
    private final CertificateCodeGenerator certificateCodeGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final CertificateRealtimeNotifier certificateRealtimeNotifier;
    private final RegistrationSubmissionNotifier submissionNotifier;
    private final LeaderLevelService leaderLevelService;
    private final CourseActivityGuard courseActivityGuard;
    private final ParticipationEligibilityChecker eligibilityChecker;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    private final ScoreCalculator scoreCalculator;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.security.UnitOperationalStatusService unitOperationalStatusService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RegistrationDecisionNotifier decisionNotifier = RegistrationDecisionNotifier.noop();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.CourseScoreVisibility courseScoreVisibility;

    @Transactional(readOnly = true)
    public Page<CourseParticipationResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scoped = SearchRequests.copy(request);
        // Hồ sơ trong phạm vi giáo phận, giáo hạt hoặc giáo xứ của tài khoản (hợp của mọi cấp tài khoản có quyền).
        List<java.util.Map<String, Object>> groups = accountScopeResolver.scopesAny(authentication,
                        List.of(CourseParticipationAccessEvaluator.READ_DIOCESE, CourseParticipationAccessEvaluator.APPROVE_DIOCESE, CourseParticipationAccessEvaluator.SCORE_DIOCESE),
                        List.of(CourseParticipationAccessEvaluator.READ_DEANERY, CourseParticipationAccessEvaluator.APPROVE_DEANERY, CourseParticipationAccessEvaluator.SCORE_DEANERY),
                        List.of(CourseParticipationAccessEvaluator.READ_PARISH))
                .groups(java.util.Map.of(
                        AccountScopeResolver.Level.DIOCESE, "course.diocese.id",
                        AccountScopeResolver.Level.DEANERY, "course.deanery.id",
                        AccountScopeResolver.Level.PARISH, "leader.parish.id"));
        if (groups.isEmpty()) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem hồ sơ đào tạo");
        }
        scoped.setScopeAnyOf(groups);
        Page<CourseParticipation> page = courseParticipationRepository.findAll(scoped);
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    // Thành phần điểm riêng của khóa (bản sao từ thành phần mẫu khi tạo khóa) để nhập điểm theo cột.
    @Transactional(readOnly = true)
    public List<ScoreEntryComponentResponse> findScoreComponents(String courseCode) {
        TrainingCourse course = requireCourse(courseCode);
        if (course.getCourseType() == null) {
            return List.of();
        }
        return courseScoreComponentRepository.findByCourseCode(courseCode)
                .stream()
                .map(item -> ScoreEntryComponentResponse.builder()
                        .id(item.getId())
                        .name(item.getName())
                        .maxScore(item.getMaxScore())
                        .coefficient(item.getCoefficient())
                        .includeInFinal(item.getIncludeInFinal())
                        .displayOrder(item.getDisplayOrder())
                        .build())
                .toList();
    }

    // Danh sách tham dự: chỉ huynh trưởng đã được duyệt đăng ký (hồ sơ chờ duyệt/bị từ chối không nằm trong danh sách).
    @Transactional(readOnly = true)
    public Page<CourseParticipationResponse> findParticipants(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest approvedOnly = SearchRequests.copy(request);
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

    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public TrainingRegistrationBatchResponse register(String courseCode, TrainingRegistrationRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(courseCode);
        courseActivityGuard.requireActive(course);
        requireActiveRegistrationData(course);
        requireRegistrationAllowed(course, authentication);

        // Đơn vị gửi: giáo xứ (khóa giáo phận/giáo hạt trong phạm vi), giáo phận (chỉ khóa giáo phận) hoặc giáo hạt (chỉ khóa giáo hạt).
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        UnitType senderType;
        UUID senderId;
        String senderName;
        senderType = registrationSenderResolver.resolveForCourse(authentication, request.getSenderType(), course);
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
            senderName = requireDeanerySenderWithinCourse(course, deaneryManaged, senderId, "gửi đăng ký").getName();
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gửi danh sách");
        OffsetDateTime submittedAt = OffsetDateTime.now();

        List<CourseParticipation> createdParticipations = new ArrayList<>();
        Map<UUID, String> exceptionReasons = normalizeExceptionReasons(request.getExceptionReasonsByLeaderId());
        List<UUID> registrationLeaderIds = normalizeLeaderIds(request.getLeaderIds(), exceptionReasons);
        Map<UUID, String> offTargetReasons = normalizeTexts(request.getOffTargetReasonsByLeaderId(), MAX_OFF_TARGET_REASON, "Lí do gửi ngoài đợt");
        Map<UUID, String> notes = normalizeTexts(request.getNotesByLeaderId(), MAX_REGISTRATION_NOTE, "Ghi chú");
        LevelOrder levelOrder = leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));
        for (UUID leaderId : registrationLeaderIds) {
            Leader leader = leaderRepository.findByIdWithParish(leaderId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy leader " + leaderId));
            if (!Boolean.TRUE.equals(leader.getStatus())) {
                throw commonValidationHelper.validationError("Chỉ được đăng ký Huynh Trưởng đang hoạt động");
            }
            requireLeaderWithinSender(leader, senderType, senderId);
            // Đóng băng: không đăng ký mới cho huynh trưởng thuộc giáo xứ đang bị khóa (hồ sơ đã có vẫn được hoàn tất).
            if (unitOperationalStatusService != null && leader.getParish() != null) {
                unitOperationalStatusService.requireUnitOperational(UnitType.PARISH, leader.getParish().getId());
            }
            if (senderType != UnitType.PARISH) {
                requireParishWithinCourseScope(course, leader.getParish());
            }

            if (courseParticipationRepository.existsByLeaderIdAndCourseCode(leaderId, courseCode)) {
                throw commonValidationHelper.conflict("Huynh Trưởng đã có hồ sơ tham gia khóa huấn luyện này");
            }
            boolean eligible = isEligible(leader, course);
            boolean exception = exceptionReasons.containsKey(leaderId);
            if (!exception && !eligible) {
                throw commonValidationHelper.validationError("Huynh Trưởng " + LeaderNames.fullName(leader) + " không đủ điều kiện, hãy đưa vào danh sách ngoại lệ kèm lí do");
            }
            if (exception && eligible) {
                throw commonValidationHelper.validationError("Huynh Trưởng " + LeaderNames.fullName(leader) + " đã đủ điều kiện, hãy đưa vào danh sách đăng ký thay vì danh sách ngoại lệ");
            }
            if (exception && exceedsNearestAchievedLevel(leader.getLeaderLevel(), course, levelOrder)) {
                throw commonValidationHelper.validationError("Huynh Trưởng " + LeaderNames.fullName(leader) + " có cấp độ cao hơn cấp độ đạt được gần nhất của loại khóa nên không thể đưa vào danh sách ngoại lệ");
            }
            String exceptionReason = exception ? exceptionReasons.get(leaderId) : null;
            // Ngoài đợt của khóa giáo phận: bắt buộc lí do riêng cho từng hồ sơ và luôn duyệt thủ công.
            String offTargetReason = null;
            if (trainingWorkflowSupport.isOffTarget(course, leader)) {
                offTargetReason = offTargetReasons.get(leaderId);
                if (offTargetReason == null) {
                    throw commonValidationHelper.validationError("Huynh Trưởng " + LeaderNames.fullName(leader)
                            + " thuộc giáo hạt ngoài đợt của khóa, hãy nhập lí do riêng cho hồ sơ này");
                }
            }

            CourseParticipation participation = newPendingParticipation(leader, course, eligible, exceptionReason, actor, submittedAt);
            participation.setOffTargetReason(offTargetReason);
            participation.setRegistrationNote(notes.get(leaderId));
            courseParticipationRepository.save(participation);
            createdParticipations.add(participation);
        }

        // Giáo hạt gửi thay giáo xứ: tài khoản quản lý của các giáo xứ có hồ sơ trong danh sách được báo.
        if (senderType == UnitType.DEANERY) {
            submissionNotifier.notifyParishManagersOfDeanerySubmission(createdParticipations, senderName, senderId, course, actor);
        }

        submissionNotifier.announce(course, createdParticipations, senderType, senderId, senderName, actor);
        return buildBatchResponse(course, senderType, senderId, senderName, createdParticipations, previewParishSubmissionAutoApprovedCount(course, createdParticipations));
    }

    // Báo cho người duyệt của đơn vị sở hữu khóa có danh sách đăng ký mới và phát sự kiện tự duyệt (hồ sơ ngoại lệ / ngoài đợt / liên giáo hạt không được tự duyệt).
    

    // Báo người duyệt có danh sách đăng ký mới và phát sự kiện tự duyệt (dùng lại cho luồng liên giáo hạt).
    public void announceSubmission(TrainingCourse course, List<CourseParticipation> createdParticipations, UnitType senderType, UUID senderId,
                                   String senderName, String actor) {
        submissionNotifier.announce(course, createdParticipations, senderType, senderId, senderName, actor);
    }

    // Danh sách huynh trưởng trong phạm vi tài khoản để lập danh sách đăng ký (eligible=true) hoặc ngoại lệ (eligible=false).
    @Transactional(readOnly = true)
    public Page<RegistrationCandidateResponse> findRegistrationCandidates(
            String courseCode,
            boolean eligibleOnly,
            String search,
            UUID deaneryId,
            UUID parishId,
            String leaderLevel,
            List<UUID> excludeLeaderIds,
            UnitType requestedSender,
            int page,
            int size,
            Authentication authentication
    ) {
        TrainingCourse course = requireCourse(courseCode);
        // Phạm vi theo tài khoản: giáo xứ -> chỉ giáo xứ đó; giáo phận -> toàn giáo phận; giáo hạt -> các giáo xứ trong giáo hạt.
        UUID scopeDioceseId = null;
        UUID scopeDeaneryId = null;
        UUID scopeParishId = null;
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        UnitType senderType = registrationSenderResolver.resolveForCourse(authentication, requestedSender, course);
        if (senderType == UnitType.PARISH) {
            scopeParishId = accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
        } else if (senderType == UnitType.DIOCESE) {
            scopeDioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE);
            if (deaneryManaged || course.getDiocese() == null || !scopeDioceseId.equals(course.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Giáo phận chỉ được lập danh sách cho khóa thuộc giáo phận của mình");
            }
        } else {
            scopeDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
            requireDeanerySenderWithinCourse(course, deaneryManaged, scopeDeaneryId, "lập danh sách");
        }
        if (scopeParishId != null && parishId != null && !parishId.equals(scopeParishId)) {
            return new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(page, size), 0);
        }
        if (scopeDeaneryId != null && deaneryId != null && !deaneryId.equals(scopeDeaneryId)) {
            return new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(page, size), 0);
        }
        UUID queryParishId = scopeParishId != null ? scopeParishId : parishId;
        UUID queryDeaneryId = scopeDeaneryId != null ? scopeDeaneryId : deaneryId;

        String levelFilter = leaderLevel == null || leaderLevel.isBlank() ? null : leaderLevel.trim();
        Set<UUID> excluded = excludeLeaderIds == null ? Set.of() : new HashSet<>(excludeLeaderIds);
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        LevelOrder candidateLevelOrder = leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));
        List<RegistrationCandidateResponse> matched = leaderRepository.findActiveInScope(scopeDioceseId, queryDeaneryId, queryParishId).stream()
                .filter(leader -> !excluded.contains(leader.getId()))
                .filter(leader -> matchesKeyword(leader, keyword))
                .filter(leader -> !courseParticipationRepository.existsByLeaderIdAndCourseCode(leader.getId(), courseCode))
                .map(leader -> toCandidateResponse(leader, isEligible(leader, course), trainingWorkflowSupport.isOffTarget(course, leader)))
                .filter(candidate -> candidate.getEligible() == eligibleOnly)
                // Lọc theo cấp huynh trưởng hiện tại (mã cấp; huynh trưởng chưa có cấp tính là NONE).
                .filter(candidate -> matchesLevelFilter(levelFilter, candidate.getLeaderLevel()))
                .filter(candidate -> eligibleOnly || !exceedsNearestAchievedLevel(candidate.getLeaderLevel(), course, candidateLevelOrder))
                .toList();

        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageImpl<>(matched.subList(from, to), org.springframework.data.domain.PageRequest.of(page, size), matched.size());
    }

    // Bộ lọc cấp huynh trưởng của danh sách ứng viên: rỗng là không lọc; huynh trưởng chưa có cấp (null) tính là NONE.
    static boolean matchesLevelFilter(String filter, String candidateLevel) {
        return filter == null || filter.equals(candidateLevel == null ? LeaderLevelService.NONE : candidateLevel);
    }

    // Cấp độ đạt được gần nhất của loại khóa: cấp thăng của khóa tiên quyết (nếu không có thì cấp hiện tại yêu cầu).
    // Huynh trưởng có cấp cao hơn mức này không được đưa vào danh sách ngoại lệ.
    public boolean exceedsNearestAchievedLevel(String leaderLevel, TrainingCourse course, LevelOrder levelOrder) {
        TrainingCourseType courseType = course.getCourseType();
        if (courseType == null || leaderLevel == null) {
            return false;
        }
        String nearest = trainingWorkflowSupport.prerequisiteOf(courseType)
                .map(TrainingCourseType::getResultingLevel)
                .orElse(courseType.getRequiredCurrentLevel());
        return nearest != null && levelOrder.higher(leaderLevel, nearest);
    }

    private boolean matchesKeyword(Leader leader, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        String haystack = String.join(" ",
                Objects.toString(LeaderNames.fullName(leader), ""),
                Objects.toString(leader.getHolyName(), ""),
                leader.getParish() == null ? "" : Objects.toString(leader.getParish().getName(), ""),
                leader.getParish() == null || leader.getParish().getDeanery() == null ? "" : Objects.toString(leader.getParish().getDeanery().getName(), "")
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    private RegistrationCandidateResponse toCandidateResponse(Leader leader, boolean eligible, boolean offTarget) {
        Parish parish = leader.getParish();
        Deanery deanery = parish == null ? null : parish.getDeanery();
        return RegistrationCandidateResponse.builder()
                .id(leader.getId())
                .holyName(leader.getHolyName())
                .fullName(LeaderNames.fullName(leader))
                .birthDate(leader.getBirthDate())
                .phoneNumber(leader.getPhoneNumber())
                .email(leader.getEmail())
                .leaderLevel(leader.getLeaderLevel())
                .parishId(parish == null ? null : parish.getId())
                .parishName(parish == null ? null : parish.getName())
                .deaneryId(deanery == null ? null : deanery.getId())
                .deaneryName(deanery == null ? null : deanery.getName())
                .eligible(eligible)
                .offTarget(offTarget)
                .build();
    }

    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public CourseParticipationResponse deaneryApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
        courseActivityGuard.requireNotEnded(participation.getCourse());
        if (!trainingWorkflowSupport.isDeaneryManaged(participation.getCourse())) {
            throw commonValidationHelper.validationError("Chỉ khóa Dự trưởng chỉ cần giáo hạt duyệt danh sách");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo hạt");
        OffsetDateTime now = OffsetDateTime.now();
        requireManualApprovalAllowed(participation.getApprovalStatus(), Boolean.TRUE.equals(request.getApproved()));
        applyApprovalDecision(participation, Boolean.TRUE.equals(request.getApproved()));
        applyAchievedLevel(participation, Boolean.TRUE.equals(request.getApproved()), request.getAchievedLevel());
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.deanery_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        decisionNotifier.decided(List.of(participation), Boolean.TRUE.equals(request.getApproved()), Collections.singletonMap(participationId, request.getReview()), actor);
        return toResponse(participation);
    }

    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public CourseParticipationResponse dioceseApprove(String participationId, TrainingApprovalRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
        courseActivityGuard.requireNotEnded(participation.getCourse());
        if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse())) {
            throw commonValidationHelper.validationError("Khóa Dự trưởng không không cần giáo phận duyệt danh sách");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt giáo phận");
        OffsetDateTime now = OffsetDateTime.now();
        requireManualApprovalAllowed(participation.getApprovalStatus(), Boolean.TRUE.equals(request.getApproved()));
        applyApprovalDecision(participation, Boolean.TRUE.equals(request.getApproved()));
        applyAchievedLevel(participation, Boolean.TRUE.equals(request.getApproved()), request.getAchievedLevel());
        participation.setReview(request.getReview());
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        publishEvent("training.registration.diocese_approved", "courseParticipation", participationId, actor, Map.of("approved", request.getApproved()));
        decisionNotifier.decided(List.of(participation), Boolean.TRUE.equals(request.getApproved()), Collections.singletonMap(participationId, request.getReview()), actor);
        return toResponse(participation);
    }

    // Chấm điểm có thể tạo/xóa chứng nhận chờ duyệt nên phải xóa cache danh sách/chi tiết chứng nhận.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public CourseParticipationResponse score(String participationId, TrainingScoreRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người nhập điểm");
        OffsetDateTime now = OffsetDateTime.now();

        // Hồ sơ đã có chứng nhận được duyệt thì không sửa điểm.
        Optional<Certificate> currentCertificate = certificateRepository.findByLeaderIdAndCourseCode(
                participation.getLeader().getId(), participation.getCourse().getCourseCode());
        if (participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.APPROVED
                || currentCertificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.APPROVED).orElse(false)) {
            throw commonValidationHelper.conflict("Không thể sửa điểm sau khi chứng chỉ đã được duyệt");
        }
        if (participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.REVOKED
                || currentCertificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.REVOKED).orElse(false)) {
            throw commonValidationHelper.conflict("Chứng nhận của hồ sơ đã bị thu hồi nên không thể sửa điểm");
        }
        requireApprovedForScoring(participation);
        requireCourseOngoingForScoring(participation.getCourse());
        if (Boolean.TRUE.equals(participation.getCourse().getScoreLocked())) {
            throw commonValidationHelper.conflict("Điểm của khóa đang bị khóa chỉnh sửa, cần mở khóa trước khi nhập điểm");
        }
        List<TrainingCourseScoreComponent> components = requireScoreComponents(participation);
        Map<String, ComponentScoreRequest> requestedScores = normalizeComponentScores(request.getComponentScores());

        // Cập nhật tại chỗ các dòng điểm đã có (không xóa rồi thêm lại): Hibernate chèn trước khi xóa nên sẽ vi phạm
        // ràng buộc duy nhất (participation_id, component_id) và báo "xung đột dữ liệu" khi sửa điểm.
        Map<String, CourseParticipationComponentScore> existingScores = new HashMap<>();
        for (CourseParticipationComponentScore existing : participation.getComponentScores()) {
            if (existing.getComponent() != null) {
                existingScores.put(existing.getComponent().getId(), existing);
            }
        }
        Set<String> applicableComponentIds = new HashSet<>();
        List<ScoreCalculator.ScoredComponent> scoredComponents = new ArrayList<>();
        // Cho phép lưu riêng từng cột điểm: thành phần không gửi lên thì giữ nguyên điểm đã lưu (nếu có).
        boolean allComponentsScored = true;
        for (TrainingCourseScoreComponent courseComponent : components) {
            TrainingCourseScoreComponent component = courseComponent;
            applicableComponentIds.add(component.getId());
            ComponentScoreRequest componentRequest = requestedScores.remove(component.getId());
            CourseParticipationComponentScore persistedScore = existingScores.get(component.getId());
            BigDecimal effectiveScore;
            if (componentRequest == null) {
                effectiveScore = persistedScore == null ? null : persistedScore.getScore();
            } else {
                if (componentRequest.getScore().compareTo(courseComponent.getMaxScore()) > 0) {
                    throw commonValidationHelper.validationError("Điểm vượt quá điểm tối đa của thành phần " + component.getName());
                }
                if (persistedScore == null) {
                    persistedScore = new CourseParticipationComponentScore();
                    persistedScore.setParticipation(participation);
                    persistedScore.setComponent(component);
                    persistedScore.setCreatedAt(now);
                    persistedScore.setCreatedBy(actor);
                    participation.getComponentScores().add(persistedScore);
                }
                persistedScore.setScore(componentRequest.getScore());
                persistedScore.setNote(commonValidationHelper.normalizeOptionalText(componentRequest.getNote()));
                persistedScore.setStatus(Boolean.TRUE);
                persistedScore.setUpdatedAt(now);
                persistedScore.setUpdatedBy(actor);
                effectiveScore = componentRequest.getScore();
            }
            if (effectiveScore == null) {
                allComponentsScored = false;
                continue;
            }
            scoredComponents.add(new ScoreCalculator.ScoredComponent(effectiveScore, courseComponent.getCoefficient(),
                    Boolean.TRUE.equals(courseComponent.getIncludeInFinal())));
        }
        if (!requestedScores.isEmpty()) {
            throw commonValidationHelper.validationError("Điểm thành phần gửi lên có thành phần không thuộc loại khóa của khóa huấn luyện");
        }
        // Thành phần không còn áp dụng cho khóa thì bỏ dòng điểm cũ.
        participation.getComponentScores().removeIf(item -> item.getComponent() == null || !applicableComponentIds.contains(item.getComponent().getId()));
        if (!allComponentsScored) {
            // Mới nhập một phần các cột điểm: chỉ lưu điểm đã nhập, chưa tính tổng kết/kết quả/chứng nhận.
            participation.setReview(request.getReview());
            if (request.getScoreNote() != null) {
                participation.setScoreNote(commonValidationHelper.normalizeOptionalText(request.getScoreNote()));
            }
            participation.setUpdatedAt(now);
            participation.setUpdatedBy(actor);
            publishEvent("training.score.updated", "courseParticipation", participationId, actor, Map.of("partial", true));
            return toResponse(participation);
        }
        // Điểm tổng kết do ScoreCalculator tính (mặc định: trung bình có hệ số của các thành phần có includeInFinal = true).
        BigDecimal totalScore = scoreCalculator.total(scoredComponents)
                .orElseThrow(() -> commonValidationHelper.validationError("Loại khóa chưa có thành phần điểm nào được tính vào điểm tổng kết"));
        participation.setTotalScore(totalScore);
        participation.setReview(request.getReview());
        if (request.getScoreNote() != null) {
            participation.setScoreNote(commonValidationHelper.normalizeOptionalText(request.getScoreNote()));
        }
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        if (scoreCalculator.isPassing(totalScore, participation.getCourse().getPassingScore())) {
            participation.setResult(CourseResult.COMPLETED);
            participation.setParticipationStatus(ParticipationStatus.COMPLETED);
            if (trainingWorkflowSupport.requiresCertificate(participation.getCourse())) {
                participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
                if (participation.getCertificateRequestedAt() == null) {
                    participation.setCertificateRequestedAt(now);
                }
                currentCertificate
                        .filter(item -> item.getApprovalStatus() == WorkflowApprovalStatus.REJECTED)
                        .ifPresent(item -> {
                            item.setApprovalStatus(WorkflowApprovalStatus.PENDING);
                            item.setRejectionReason(null);
                            item.setUpdatedAt(now);
                            item.setUpdatedBy(actor);
                        });
                ensureSuggestedCertificateExists(participation, actor);
            } else {
                // Khóa không có cấp độ đạt được: chỉ cần điểm đạt, không tạo chứng nhận.
                participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
                participation.setCertificateRequestedAt(null);
            }
        } else {
            participation.setResult(CourseResult.UNCOMPLETED);
            participation.setParticipationStatus(ParticipationStatus.ATTENDED);
            // Chứng nhận ngoại lệ đang chờ duyệt (do người chấm điểm tạo cho hồ sơ không đạt) được giữ nguyên khi sửa điểm.
            boolean keepException = currentCertificate
                    .filter(item -> Boolean.TRUE.equals(item.getExceptionRequested()))
                    .filter(item -> item.getApprovalStatus() == WorkflowApprovalStatus.PENDING || item.getApprovalStatus() == WorkflowApprovalStatus.SUBMITTED)
                    .isPresent();
            if (!keepException) {
                participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
                participation.setCertificateRequestedAt(null);
                // Sửa điểm xuống dưới điểm đạt: xóa chứng nhận đang chờ duyệt của hồ sơ.
                currentCertificate
                        .filter(item -> item.getApprovalStatus() == WorkflowApprovalStatus.PENDING
                                || item.getApprovalStatus() == WorkflowApprovalStatus.SUBMITTED
                                || item.getApprovalStatus() == WorkflowApprovalStatus.REJECTED)
                        .ifPresent(certificateRepository::delete);
            }
        }
        publishEvent("training.score.updated", "courseParticipation", participationId, actor, Map.of("score", totalScore));
        certificateRealtimeNotifier.notifyChanged("SCORE_UPDATED", participation.getCourse().getCourseCode(), null);
        return toResponse(participation);
    }

    /**
     * Tạo chứng nhận ngoại lệ (chờ duyệt) cho hồ sơ đã chấm điểm nhưng không đạt: người chấm điểm đề xuất, người duyệt
     * quyết định ở tab duyệt chứng nhận (bắt buộc nhập lý do khi duyệt). Chỉ khóa thường có cấp độ đạt được và chưa kết thúc.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public CourseParticipationResponse createExceptionCertificate(String participationId, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        TrainingCourse course = participation.getCourse();
        courseActivityGuard.requireActive(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo chứng nhận ngoại lệ");
        if (course.isPast()) {
            throw commonValidationHelper.conflict("Khóa quá khứ không tạo chứng nhận ngoại lệ");
        }
        if (course.getFinishedAt() != null) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã kết thúc nên không thể tạo chứng nhận ngoại lệ");
        }
        if (!trainingWorkflowSupport.requiresCertificate(course)) {
            throw commonValidationHelper.conflict("Khóa huấn luyện không cấp chứng nhận nên không có chứng nhận");
        }
        requireApprovedForScoring(participation);
        if (participation.getTotalScore() == null || participation.getResult() == CourseResult.COMPLETED) {
            throw commonValidationHelper.conflict("Chỉ tạo chứng nhận ngoại lệ cho hồ sơ đã chấm điểm và có kết quả không đạt");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Optional<Certificate> existing = certificateRepository.findByLeaderIdAndCourseCode(participation.getLeader().getId(), course.getCourseCode());
        if (existing.isPresent() && existing.get().getApprovalStatus() != WorkflowApprovalStatus.REJECTED) {
            throw commonValidationHelper.conflict("Hồ sơ đã có chứng nhận");
        }
        if (existing.isPresent()) {
            // Chứng nhận từng bị từ chối: đưa lại về chờ duyệt dưới dạng ngoại lệ.
            Certificate certificate = existing.get();
            certificate.setApprovalStatus(WorkflowApprovalStatus.PENDING);
            certificate.setRejectionReason(null);
            certificate.setUpdatedAt(now);
            certificate.setUpdatedBy(actor);
            certificate.setSystemSuggested(Boolean.FALSE);
            certificate.setExceptionRequested(Boolean.TRUE);
        } else {
            ensureSuggestedCertificateExists(participation, actor);
            certificateRepository.findByLeaderIdAndCourseCode(participation.getLeader().getId(), course.getCourseCode()).ifPresent(certificate -> {
                certificate.setSystemSuggested(Boolean.FALSE);
                certificate.setExceptionRequested(Boolean.TRUE);
            });
        }
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
        participation.setCertificateRequestedAt(now);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        publishEvent("training.certificate.exception_requested", "courseParticipation", participationId, actor, Map.of("courseCode", course.getCourseCode()));
        certificateRealtimeNotifier.notifyChanged("EXCEPTION_REQUESTED", course.getCourseCode(), null);
        return toResponse(participation);
    }

    // Lưu riêng nhận xét kết quả khóa của hồ sơ: vẫn được khi điểm đã khóa (kể cả chứng nhận đã duyệt/thu hồi),
    // nhưng không được khi khóa đã kết thúc hoặc bị vô hiệu hóa.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @Transactional
    public CourseParticipationResponse updateScoreNote(String participationId, TrainingScoreNoteRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
        if (participation.getCourse().getFinishedAt() != null) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã kết thúc nên không thể sửa nhận xét");
        }
        participation.setScoreNote(commonValidationHelper.normalizeOptionalText(request == null ? null : request.getNote()));
        participation.setUpdatedAt(OffsetDateTime.now());
        participation.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người sửa nhận xét"));
        return toResponse(participation);
    }

    // Sửa ghi chú đăng ký: người duyệt đúng đơn vị sở hữu khóa (mọi hồ sơ của khóa) hoặc người đã gửi hồ sơ khi hồ sơ còn chờ duyệt.
    // Khóa bị vô hiệu hóa hoặc đã kết thúc thì chỉ xem.
    @Transactional
    public CourseParticipationResponse updateRegistrationNote(String participationId, TrainingRegistrationNoteRequest request, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
        courseActivityGuard.requireNotEnded(participation.getCourse());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người sửa ghi chú");
        boolean sender = actor.equals(participation.getCreatedBy())
                && (participation.getApprovalStatus() == WorkflowApprovalStatus.PENDING || participation.getApprovalStatus() == WorkflowApprovalStatus.SUBMITTED);
        if (!sender && !canApproveCourse(participation.getCourse(), authentication)) {
            throw commonValidationHelper.forbidden("Không có quyền sửa ghi chú của hồ sơ này");
        }
        String note = request == null || request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        if (note != null && note.length() > MAX_REGISTRATION_NOTE) {
            throw commonValidationHelper.validationError("Ghi chú tối đa " + MAX_REGISTRATION_NOTE + " ký tự");
        }
        participation.setRegistrationNote(note);
        participation.setUpdatedAt(OffsetDateTime.now());
        participation.setUpdatedBy(actor);
        return toResponse(participation);
    }

    private List<TrainingCourseScoreComponent> requireScoreComponents(CourseParticipation participation) {
        if (participation.getCourse() == null || participation.getCourse().getCourseType() == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa có loại khóa");
        }
        List<TrainingCourseScoreComponent> components = courseScoreComponentRepository.findByCourseCode(participation.getCourse().getCourseCode());
        if (components.isEmpty()) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa có thành phần điểm nào");
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

    // Tạo chứng nhận đề xuất (chờ duyệt) cho hồ sơ nếu chưa có. Dùng chung cho chấm điểm và import khóa quá khứ.
    public void ensureSuggestedCertificateExists(CourseParticipation participation, String actor) {
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
        // GP cho khóa giáo phận, GH cho khóa giáo hạt.
        String unitPrefix = trainingWorkflowSupport.isDeaneryManaged(participation.getCourse()) ? "GH" : "GP";
        if (participation.getCourse().isPast()) {
            // Khóa quá khứ không có loại khóa: dùng ký hiệu chung QK trong mã chứng nhận.
            String pastPrefix = certificateCodeGenerator.buildPrefixWithShortCode(unitPrefix, unitName,
                    phucuong.ht.modules.certificate.CertificateCodeGenerator.PAST_COURSE_SHORT_CODE, participation.getCourse().getHostYear());
            certificateRepository.lockCodePrefix(pastPrefix);
            long pastSequence = certificateRepository.findHighestSequenceByCodePrefix(pastPrefix).orElse(0L) + 1L;
            return certificateCodeGenerator.buildCodeWithShortCode(unitPrefix, unitName,
                    phucuong.ht.modules.certificate.CertificateCodeGenerator.PAST_COURSE_SHORT_CODE, participation.getCourse().getHostYear(), pastSequence);
        }
        String prefix = certificateCodeGenerator.buildPrefix(unitPrefix, unitName, participation.getCourse().getCourseType(), participation.getCourse().getHostYear());
        certificateRepository.lockCodePrefix(prefix);
        long nextSequence = certificateRepository.findHighestSequenceByCodePrefix(prefix).orElse(0L) + 1L;
        return certificateCodeGenerator.buildCode(unitPrefix, unitName, participation.getCourse().getCourseType(), participation.getCourse().getHostYear(), nextSequence);
    }

    private String unitNameForCertificate(TrainingCourse course) {
        if (trainingWorkflowSupport.isDeaneryManaged(course)) {
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

    // Khóa không mở đăng ký (registrationEnabled = false): người duyệt đăng ký đúng cấp quản lý khóa vẫn đăng ký được cho tới khi khóa diễn ra;
    // các trường hợp còn lại theo cửa sổ đăng ký thông thường.
    public void requireRegistrationAllowed(TrainingCourse course, Authentication authentication) {
        if (Boolean.FALSE.equals(course.getRegistrationEnabled()) && canApproveCourse(course, authentication)) {
            if (course.isPast() || TrainingCourseStatusSupport.isCourseStarted(course, OffsetDateTime.now())) {
                throw commonValidationHelper.validationError("Khóa huấn luyện đã diễn ra nên đã đóng đăng ký");
            }
            return;
        }
        requireRegistrationWindowOpen(course);
    }

    public void requireRegistrationWindowOpen(TrainingCourse course) {
        OffsetDateTime now = OffsetDateTime.now();
        if (Boolean.FALSE.equals(course.getRegistrationEnabled())) {
            throw commonValidationHelper.validationError("Khóa huấn luyện không mở đăng ký");
        }
        if (course.getRegistrationStartAt() == null || course.getRegistrationEndAt() == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa cấu hình thời gian nhận đăng ký");
        }
        if (now.isBefore(course.getRegistrationStartAt())) {
            throw commonValidationHelper.validationError("Chưa đến thời gian nhận đăng ký");
        }
        if (now.isAfter(course.getRegistrationEndAt())) {
            throw commonValidationHelper.validationError("Đã hết thời gian nhận đăng ký");
        }
        if (TrainingCourseStatusSupport.isCourseStarted(course, now)) {
            throw commonValidationHelper.validationError("Khóa huấn luyện đã diễn ra nên đã đóng đăng ký");
        }
        if (TrainingCourseStatusSupport.registrationStatus(course, now) == CourseRegistrationStatus.CLOSED) {
            throw commonValidationHelper.validationError("Khóa huấn luyện đang đóng đăng ký");
        }
    }

    public void requireActiveRegistrationData(TrainingCourse course) {
        if (!Boolean.TRUE.equals(course.getStatus())) {
            throw commonValidationHelper.validationError("Khóa huấn luyện đã ngừng hoạt động");
        }
        if (course.getCourseType() != null && !Boolean.TRUE.equals(course.getCourseType().getStatus())) {
            throw commonValidationHelper.validationError("Loại khóa huấn luyện đã ngừng hoạt động");
        }
    }

    // Giáo hạt gửi được cho khóa giáo hạt của mình và khóa giáo phận mà giáo hạt trực thuộc.
    private Deanery requireDeanerySenderWithinCourse(TrainingCourse course, boolean deaneryManaged, UUID deaneryId, String action) {
        Deanery deanery = deaneryRepository.findById(deaneryId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt " + deaneryId));
        if (deaneryManaged) {
            if (course.getDeanery() == null || !deaneryId.equals(course.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Giáo hạt chỉ được " + action + " cho khóa giáo hạt của mình hoặc khóa thuộc giáo phận của mình");
            }
        } else if (course.getDiocese() == null || deanery.getDiocese() == null
                || !course.getDiocese().getId().equals(deanery.getDiocese().getId())) {
            throw commonValidationHelper.forbidden("Giáo hạt chỉ được " + action + " cho khóa giáo hạt của mình hoặc khóa thuộc giáo phận của mình");
        }
        return deanery;
    }

    

    private void requireParishWithinCourseScope(TrainingCourse course, Parish parish) {
        if (parish == null || parish.getDeanery() == null) {
            throw commonValidationHelper.forbidden("Giáo xứ hiện tại không có phạm vi giáo hạt hợp lệ để gửi đăng ký");
        }
        if (trainingWorkflowSupport.isDeaneryManaged(course)) {
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

    public static final int MAX_OFF_TARGET_REASON = 500;
    public static final int MAX_REGISTRATION_NOTE = 1000;

    // Hồ sơ chờ duyệt mới (dùng chung cho gửi đăng ký và liên giáo hạt); người gọi tự lưu và gắn các trường riêng.
    public CourseParticipation newPendingParticipation(Leader leader, TrainingCourse course, boolean eligible, String exceptionReason,
                                                       String actor, OffsetDateTime submittedAt) {
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
        return participation;
    }

    // Văn bản tùy chọn theo hồ sơ: bỏ khóa null và giá trị trống, cắt khoảng trắng, quá độ dài thì báo lỗi.
    public Map<UUID, String> normalizeTexts(Map<UUID, String> source, int maxLength, String label) {
        Map<UUID, String> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        source.forEach((leaderId, text) -> {
            if (leaderId == null || text == null || text.isBlank()) {
                return;
            }
            String trimmed = text.trim();
            if (trimmed.length() > maxLength) {
                throw commonValidationHelper.validationError(label + " tối đa " + maxLength + " ký tự");
            }
            normalized.put(leaderId, trimmed);
        });
        return normalized;
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
    

    // Hồ sơ gửi ngoài đợt hoặc liên giáo hạt luôn duyệt thủ công, kể cả khi đủ điều kiện và khóa bật tự duyệt.
    public static boolean requiresManualApproval(CourseParticipation participation) {
        return participation.getOffTargetReason() != null || participation.getCrossDeaneryReason() != null;
    }

    private int previewParishSubmissionAutoApprovedCount(TrainingCourse course, List<CourseParticipation> participations) {
        if (!trainingWorkflowSupport.shouldAutoApproveAfterSubmission(course.getCourseType())
                || !Boolean.TRUE.equals(course.getAutoApproveEligible())) {
            return 0;
        }
        return (int) participations.stream().filter(participation -> Boolean.TRUE.equals(participation.getEligible()) && !requiresManualApproval(participation)).count();
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
                .participations(toResponses(participations))
                .build();
    }

    

    

    private boolean isEligible(Leader leader, TrainingCourse course) {
        return isEligible(leader.getLeaderLevel(), leader.getBirthDate(), leader.getId(), course);
    }

    public boolean isEligible(String leaderLevel, LocalDate birthDate, UUID leaderId, TrainingCourse course) {
        return eligibilityChecker.isEligible(leaderLevel, birthDate, leaderId, course);
    }

    // Nhiều hồ sơ cùng khóa dùng chung một lần tải thành phần điểm của khóa (tránh truy vấn lặp theo từng hồ sơ).
    private List<CourseParticipationResponse> toResponses(List<CourseParticipation> participations) {
        Map<String, Map<String, TrainingCourseScoreComponent>> cache = new HashMap<>();
        return participations.stream().map(participation -> toResponse(participation, cache)).toList();
    }

    private CourseParticipationResponse toResponse(CourseParticipation participation) {
        return toResponse(participation, new HashMap<>());
    }

    private CourseParticipationResponse toResponse(CourseParticipation participation, Map<String, Map<String, TrainingCourseScoreComponent>> componentCache) {
        boolean showScores = courseScoreVisibility == null || courseScoreVisibility.canSeeScores(participation.getCourse());
        return CourseParticipationResponse.builder()
                .id(participation.getId())
                .leaderId(participation.getLeader() == null ? null : participation.getLeader().getId())
                .leaderHolyName(participation.getLeader() == null ? null : participation.getLeader().getHolyName())
                .leaderFullName(LeaderNames.fullName(participation.getLeader()))
                .courseCode(participation.getCourse() == null ? null : participation.getCourse().getCourseCode())
                .courseName(participation.getCourse() == null ? null : participation.getCourse().getCourseName())
                .coursePast(participation.getCourse() != null && participation.getCourse().isPast())
                .courseFinished(participation.getCourse() != null && TrainingCourseStatusSupport.organizingStatus(participation.getCourse(), TrainingCourseStatusSupport.today())
                        == phucuong.ht.modules.training.enums.CourseOrganizingStatus.ENDED)
                .courseTypeCode(participation.getCourse() == null || participation.getCourse().getCourseType() == null ? null : participation.getCourse().getCourseType().getTypeCode())
                .registrationDate(participation.getRegistrationDate())
                .participationStatus(participation.getParticipationStatus())
                .scoreHidden(!showScores)
                .totalScore(showScores ? participation.getTotalScore() : null)
                .passingScore(participation.getCourse() == null || !showScores ? null : participation.getCourse().getPassingScore())
                .passed(participation.getCourse() != null
                        && participation.getTotalScore() != null
                        && participation.getTotalScore().compareTo(participation.getCourse().getPassingScore()) >= 0)
                .result(participation.getResult())
                .review(participation.getReview())
                .scoreNote(participation.getScoreNote())
                .eligible(participation.getEligible())
                .exceptionReason(participation.getExceptionReason())
                .registrationNote(participation.getRegistrationNote())
                .offTarget(participation.getOffTargetReason() != null)
                .offTargetReason(participation.getOffTargetReason())
                .crossDeanery(participation.getCrossDeaneryReason() != null)
                .crossDeaneryReason(participation.getCrossDeaneryReason())
                .homeDeaneryName(participation.getHomeDeaneryId() == null ? null
                        : deaneryRepository.findById(participation.getHomeDeaneryId()).map(Deanery::getName).orElse(null))
                .homeDeaneryApprovedBy(auditActorNameResolver.resolve(participation.getHomeDeaneryApprovedBy()))
                .homeDeaneryApprovedAt(participation.getHomeDeaneryApprovedAt())
                .leaderLevel(participation.getLeader() == null ? null : participation.getLeader().getLeaderLevel())
                .courseResultingLevel(participation.getCourse() == null ? null : trainingWorkflowSupport.certificateLevelFor(participation.getCourse()).orElse(null))
                .targetLevel(participation.getTargetLevel())
                .parishSubmittedAt(participation.getParishSubmittedAt())
                .approvalStatus(participation.getApprovalStatus())
                .createdBy(participation.getCreatedBy())
                .createdAt(participation.getCreatedAt())
                .createdByName(auditActorNameResolver.resolve(participation.getCreatedBy()))
                .updatedAt(participation.getUpdatedAt())
                .updatedBy(participation.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(participation.getUpdatedBy()))
                .parishId(participation.getLeader() == null || participation.getLeader().getParish() == null ? null : participation.getLeader().getParish().getId())
                .parishName(participation.getLeader() == null || participation.getLeader().getParish() == null ? null : participation.getLeader().getParish().getName())
                .deaneryId(participation.getLeader() == null || participation.getLeader().getParish() == null || participation.getLeader().getParish().getDeanery() == null ? null : participation.getLeader().getParish().getDeanery().getId())
                .deaneryName(participation.getLeader() == null || participation.getLeader().getParish() == null || participation.getLeader().getParish().getDeanery() == null ? null : participation.getLeader().getParish().getDeanery().getName())
                .managementUnitType(participation.getCourse() == null || participation.getCourse().getCourseType() == null ? null : participation.getCourse().getCourseType().getManagementUnitType())
                .courseDioceseId(participation.getCourse() == null || participation.getCourse().getDiocese() == null ? null : participation.getCourse().getDiocese().getId())
                .courseDeaneryId(participation.getCourse() == null || participation.getCourse().getDeanery() == null ? null : participation.getCourse().getDeanery().getId())
                .certificateApprovalStatus(participation.getCertificateApprovalStatus())
                .certificateRequestedAt(participation.getCertificateRequestedAt())
                .certificateApprovedAt(participation.getCertificateApprovedAt())
                .finalResult(participation.getFinalResult())
                .approvedBy(participation.getApprovedBy())
                .teamName(participation.getTeamName())
                .checkedInAt(participation.getCheckedInAt())
                .checkedInBy(participation.getCheckedInBy())
                .invitationSentAt(participation.getInvitationSentAt())
                .invitationError(participation.getInvitationError())
                .attendanceEnabled(participation.getCourse() != null && Boolean.TRUE.equals(participation.getCourse().getAttendanceEnabled()))
                .leaderEmail(participation.getLeader() == null ? null : participation.getLeader().getEmail())
                .leaderPhone(participation.getLeader() == null ? null : participation.getLeader().getPhoneNumber())
                .leaderGender(participation.getLeader() == null || participation.getLeader().getGender() == null ? null : participation.getLeader().getGender().name())
                .approvedByName(auditActorNameResolver.resolve(participation.getApprovedBy()))
                // Thành phần điểm đã bị vô hiệu hóa không hiển thị (khớp với danh sách cột ở /score-components).
                .componentScores(!showScores ? List.<ComponentScoreResponse>of() : componentScoreResponses(participation, componentCache))
                .status(participation.getStatus())
                .build();
    }

    // Điểm thành phần của hồ sơ, chỉ gồm các thành phần đang thuộc khóa; điểm tối đa/hệ số lấy theo giá trị riêng của khóa.
    private List<ComponentScoreResponse> componentScoreResponses(CourseParticipation participation, Map<String, Map<String, TrainingCourseScoreComponent>> componentCache) {
        if (participation.getCourse() == null) {
            return List.of();
        }
        Map<String, TrainingCourseScoreComponent> byComponentId = componentCache.computeIfAbsent(participation.getCourse().getCourseCode(), code -> {
            Map<String, TrainingCourseScoreComponent> loaded = new HashMap<>();
            for (TrainingCourseScoreComponent item : courseScoreComponentRepository.findByCourseCode(code)) {
                loaded.put(item.getId(), item);
            }
            return loaded;
        });
        return participation.getComponentScores().stream()
                .filter(score -> score.getComponent() != null && byComponentId.containsKey(score.getComponent().getId()))
                .sorted(Comparator.comparing(score -> byComponentId.get(score.getComponent().getId()).getDisplayOrder(), Comparator.nullsLast(Integer::compareTo)))
                .map(score -> {
                    TrainingCourseScoreComponent courseComponent = byComponentId.get(score.getComponent().getId());
                    return ComponentScoreResponse.builder()
                            .id(score.getId())
                            .componentId(score.getComponent().getId())
                            .componentName(courseComponent.getName())
                            .maxScore(courseComponent.getMaxScore())
                            .coefficient(courseComponent.getCoefficient())
                            .includeInFinal(courseComponent.getIncludeInFinal())
                            .score(score.getScore())
                            .note(score.getNote())
                            .displayOrder(courseComponent.getDisplayOrder())
                            .build();
                })
                .toList();
    }

    // Thu hồi đăng ký: chỉ người gửi đăng ký, và chỉ khi hồ sơ còn chờ duyệt. Hồ sơ bị xóa để huynh trưởng có thể được đăng ký lại.
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public void withdraw(String participationId, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy đăng ký khóa huấn luyện " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
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
    // Hồ sơ không bị xóa hẳn mà chuyển về trạng thái bị từ chối duyệt (xóa điểm) để có thể duyệt lại.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public void removeParticipant(String participationId, Authentication authentication) {
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ tham dự " + participationId));
        courseActivityGuard.requireActive(participation.getCourse());
        courseActivityGuard.requireNotEnded(participation.getCourse());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người xóa hồ sơ");
        if (!canApproveCourseOf(participation, authentication)) {
            throw commonValidationHelper.forbidden("Không có quyền xóa hồ sơ khỏi danh sách tham dự");
        }
        if (participation.getApprovalStatus() != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Chỉ xóa được hồ sơ đang nằm trong danh sách tham dự");
        }
        UUID leaderId = participation.getLeader().getId();
        String courseCode = participation.getCourse().getCourseCode();
        Optional<Certificate> certificate = certificateRepository.findByLeaderIdAndCourseCode(leaderId, courseCode);
        boolean hasCertificate = participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.APPROVED
                || certificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.APPROVED).orElse(false);
        if (hasCertificate) {
            throw commonValidationHelper.conflict("Không thể xóa hồ sơ đã có chứng nhận");
        }
        // Chứng nhận hệ thống đề xuất (chưa duyệt) sinh ra khi chấm điểm đạt bị xóa vì kết quả chấm không còn hiệu lực.
        certificate.ifPresent(certificateRepository::delete);
        applyApprovalDecision(participation, false);
        resetScoring(participation);
        participation.setReview(REMOVED_FROM_PARTICIPANTS_REVIEW);
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(OffsetDateTime.now());
        participation.setUpdatedBy(actor);
        // Người gửi đăng ký và admin giáo xứ của hồ sơ được báo hồ sơ đã bị xóa khỏi danh sách tham dự.
        decisionNotifier.removed(participation, actor);
        publishEvent("training.participant.removed", "courseParticipation", participationId, actor,
                Map.of("courseCode", courseCode, "leaderId", String.valueOf(leaderId)));
        // Chứng nhận chờ duyệt của hồ sơ bị xóa theo: báo realtime để tab duyệt chứng nhận và màn chứng nhận tải lại.
        certificateRealtimeNotifier.notifyChanged("PARTICIPANT_REMOVED", courseCode, null);
    }

    // Duyệt hoặc từ chối nhiều hồ sơ một lần; hồ sơ không hợp lệ được bỏ qua và trả về kèm lý do.
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public TrainingBulkApprovalResponse bulkApprove(TrainingBulkApprovalRequest request, Authentication authentication) {
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt");
        OffsetDateTime now = OffsetDateTime.now();
        List<TrainingBulkApprovalResponse.Failure> failures = new ArrayList<>();
        int success = 0;
        List<CourseParticipation> decided = new ArrayList<>();
        Map<String, String> reviewsById = new HashMap<>();
        for (String id : new LinkedHashSet<>(request.getIds())) {
            Optional<CourseParticipation> found = courseParticipationRepository.findByIdWithLeaderAndCourse(id);
            if (found.isEmpty()) {
                failures.add(bulkFailure(id, null, "Không tìm thấy đăng ký"));
                continue;
            }
            CourseParticipation participation = found.get();
            String leaderName = LeaderNames.fullName(participation.getLeader());
            String problem = bulkApprovalProblem(participation, authentication, approved);
            if (problem != null) {
                failures.add(bulkFailure(id, leaderName, problem));
                continue;
            }
            String own = request.getReviews() == null ? null : request.getReviews().get(id);
            String review = own != null && !own.isBlank() ? own : request.getReview();
            if (!approved && (review == null || review.isBlank())) {
                failures.add(bulkFailure(id, leaderName, "Phải nhập lý do khi từ chối hồ sơ"));
                continue;
            }
            try {
                String ownLevel = request.getAchievedLevels() == null ? null : request.getAchievedLevels().get(id);
                applyAchievedLevel(participation, approved, ownLevel != null && !ownLevel.isBlank() ? ownLevel : request.getAchievedLevel());
            } catch (RuntimeException ex) {
                failures.add(bulkFailure(id, leaderName, ex.getMessage()));
                continue;
            }
            applyApprovalDecision(participation, approved);
            participation.setReview(review);
            participation.setApprovedBy(actor);
            participation.setUpdatedAt(now);
            participation.setUpdatedBy(actor);
            boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(participation.getCourse());
            publishEvent(deaneryManaged ? "training.registration.deanery_approved" : "training.registration.diocese_approved",
                    "courseParticipation", id, actor, Map.of("approved", approved));
            decided.add(participation);
            reviewsById.put(id, review);
            success++;
        }
        // Một lần duyệt/từ chối hàng loạt: từ 3 hồ sơ cùng khóa thì người nhận chỉ có một thông báo gom nhóm.
        decisionNotifier.decided(decided, approved, reviewsById, actor);
        return TrainingBulkApprovalResponse.builder()
                .successCount(success)
                .failedCount(failures.size())
                .failures(failures)
                .build();
    }

    private TrainingBulkApprovalResponse.Failure bulkFailure(String id, String leaderName, String reason) {
        return TrainingBulkApprovalResponse.Failure.builder().id(id).leaderFullName(leaderName).reason(reason).build();
    }

    // Quyền duyệt theo cấp quản lý của khóa (giống endpoint duyệt từng hồ sơ); hồ sơ phải đang chờ duyệt hoặc (khi duyệt lại) đã bị từ chối.
    private String bulkApprovalProblem(CourseParticipation participation, Authentication authentication, boolean approved) {
        if (!canApproveCourseOf(participation, authentication)) {
            return "Không có quyền duyệt hồ sơ này";
        }
        if (participation.getCourse() != null && Boolean.FALSE.equals(participation.getCourse().getStatus())) {
            return "Khóa huấn luyện đã bị vô hiệu hóa nên không thể thao tác";
        }
        if (courseActivityGuard.isEnded(participation.getCourse())) {
            return "Khóa huấn luyện đã kết thúc nên không thể thao tác";
        }
        if (!isManualDecisionAllowed(participation.getApprovalStatus(), approved)) {
            return "Hồ sơ đã ở trạng thái kết thúc hoặc không chờ duyệt";
        }
        // Duyệt hàng loạt chỉ áp dụng cho hồ sơ đủ điều kiện; hồ sơ ngoại lệ phải duyệt riêng để chọn cấp độ đạt được.
        if (approved && Boolean.FALSE.equals(participation.getEligible())) {
            return "Hồ sơ ngoại lệ phải được duyệt riêng từng hồ sơ";
        }
        return null;
    }

    // Tài khoản có quyền duyệt đúng cấp quản lý của khóa: giáo hạt với khóa giáo hạt, giáo phận với khóa giáo phận.
    private boolean canApproveCourseOf(CourseParticipation participation, Authentication authentication) {
        return canApproveCourse(participation.getCourse(), authentication);
    }

    // Người duyệt đăng ký đúng đơn vị sở hữu khóa (giáo hạt với khóa giáo hạt, giáo phận với khóa giáo phận).
    // Kiểm tra cho một thao tác: có quyền duyệt mà tài khoản không gắn đơn vị đúng cấp thì báo lỗi phạm vi.
    public boolean canApproveCourse(TrainingCourse course, Authentication authentication) {
        return phucuong.ht.modules.training.CourseApproverRule.canApprove(trainingWorkflowSupport.isDeaneryManaged(course), course, authentication,
                () -> accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseParticipationAccessEvaluator.APPROVE_DIOCESE),
                () -> accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.APPROVE_DEANERY));
    }

    // Hồ sơ ngoại lệ (chưa đủ điều kiện) khi duyệt phải có cấp độ đạt được do người duyệt chọn:
    // cao hơn cấp hiện tại của huynh trưởng và không cao hơn cấp độ đạt được của loại khóa.
    // Hồ sơ đủ điều kiện hoặc loại khóa không có cấp độ đạt được thì không cần chọn.
    private void applyAchievedLevel(CourseParticipation participation, boolean approved, String requestedLevel) {
        if (!approved || Boolean.TRUE.equals(participation.getEligible())) {
            return;
        }
        TrainingCourseType courseType = participation.getCourse() == null ? null : participation.getCourse().getCourseType();
        String courseLevel = courseType == null ? null : courseType.getResultingLevel();
        if (courseLevel == null || "NONE".equals(courseLevel)) {
            return;
        }
        String level = leaderLevelService.requireAchievable(requestedLevel, participation.getLeader().getLeaderLevel(), courseLevel,
                trainingWorkflowSupport.dioceseIdOf(participation.getCourse()));
        participation.setTargetLevel(level);
    }

    // Duyệt xong -> đã tham dự (ATTENDED); từ chối duyệt -> bị từ chối (REJECTED).
    private void applyApprovalDecision(CourseParticipation participation, boolean approved) {
        participation.setApprovalStatus(approved ? WorkflowApprovalStatus.APPROVED : WorkflowApprovalStatus.REJECTED);
        participation.setParticipationStatus(approved ? ParticipationStatus.ATTENDED : ParticipationStatus.REJECTED);
        // Hồ sơ ra khỏi danh sách tham dự thì không còn thuộc đội nào.
        if (!approved) {
            participation.setTeamName(null);
            participation.setCheckedInAt(null);
            participation.setCheckedInBy(null);
            participation.setInvitationSentAt(null);
            participation.setInvitationError(null);
        }
    }

    // Hồ sơ chờ duyệt được duyệt hoặc từ chối; hồ sơ đã bị từ chối chỉ được duyệt lại.
    private void requireManualApprovalAllowed(WorkflowApprovalStatus status, boolean approved) {
        if (!isManualDecisionAllowed(status, approved)) {
            throw commonValidationHelper.conflict("Hồ sơ đã ở trạng thái kết thúc hoặc không chờ duyệt");
        }
    }

    private boolean isManualDecisionAllowed(WorkflowApprovalStatus status, boolean approved) {
        return ApprovalTransitionPolicy.canDecideManually(status, approved);
    }

    // Hồ sơ trở về trạng thái bị từ chối thì kết quả chấm điểm cũ không còn hiệu lực.
    private void resetScoring(CourseParticipation participation) {
        participation.getComponentScores().clear();
        participation.setTotalScore(null);
        participation.setResult(null);
        participation.setFinalResult(null);
        participation.setScoreNote(null);
        participation.setTargetLevel(null);
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
        participation.setCertificateRequestedAt(null);
        participation.setCertificateApprovedAt(null);
    }

    // Chỉ được chấm điểm khi khóa đang trong thời gian diễn ra (từ ngày bắt đầu đến hết ngày kết thúc, múi giờ Việt Nam).
    private void requireCourseOngoingForScoring(TrainingCourse course) {
        if (TrainingCourseStatusSupport.organizingStatus(course, TrainingCourseStatusSupport.today()) != CourseOrganizingStatus.ONGOING) {
            throw commonValidationHelper.conflict("Chỉ được chấm điểm khi khóa huấn luyện đang trong thời gian diễn ra");
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

    private void publishEvent(String eventType, String entityType, String entityId, String actor, Map<String, Object> payload) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.of(eventType, entityType, entityId, actor, payload));
    }

    public static String autoApprovalActor() {
        return AUTO_APPROVAL_SYSTEM_ACTOR;
    }
}
