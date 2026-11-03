package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import phucuong.ht.common.security.AuthorityChecks;
import phucuong.ht.common.utils.LeaderNames;
import phucuong.ht.common.repository.SearchRequests;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseScoreComponent.repository.TrainingCourseScoreComponentRepository;
import phucuong.ht.modules.training.courseScoreComponent.service.CourseScoreComponentService;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.CourseRecipientCandidateResponse;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.certificate.service.CertificateLifecycleService;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseStatusToggleRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.CourseStatusImpactResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.security.AccountScopeResolver;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseCreateRequest;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingCourseService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";
    public static final String EVENT_REGISTRATION_OPENED = "training.course.registration_opened";
    public static final String EVENT_REGISTRATION_CLOSED = "training.course.registration_closed";
    private static final String AUTO_ACTOR = "SYSTEM_AUTO_REGISTRATION";

    private final TrainingCourseRepository trainingCourseRepository;
    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final CourseTypeDioceseGuard courseTypeDioceseGuard;
    private final CourseParticipationRepository courseParticipationRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final AccountScopeResolver accountScopeResolver;
    private final phucuong.ht.modules.training.RegistrationSenderResolver registrationSenderResolver;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;
    private final CourseScoreComponentService courseScoreComponentService;
    private final TrainingCourseScoreComponentRepository courseScoreComponentRepository;
    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final AccountRepository accountRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final AccountRoleRepository accountRoleRepository;
    private final CourseFinishService courseFinishService;
    private final CertificateLifecycleService certificateLifecycleService;
    private final CertificateRepository certificateRepository;
    private final CourseActivityGuard courseActivityGuard;
    private final phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;
    private final phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier certificateRealtimeNotifier;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.TrainingDecisionNotifier trainingDecisionNotifier;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.crossDeanery.service.CrossDeaneryAccessService crossDeaneryAccessService;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseResponse create(TrainingCourseCreateRequest request, Authentication authentication) {
        TrainingCourseType courseType = resolveCourseType(request.getCourseTypeCode(), authentication);
        String auditActor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo khóa huấn luyện");
        OffsetDateTime now = OffsetDateTime.now();

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(generateUniqueCourseCode());
        course.setCourseType(courseType);
        course.setCourseName(request.getCourseName());
        course.setHostYear(request.getHostYear());
        course.setYearsActive(request.getYearsActive() == null ? 0 : request.getYearsActive());
        course.setLocation(request.getLocation());
        course.setStartDate(request.getStartDate());
        course.setEndDate(request.getEndDate());
        course.setRegistrationStartAt(request.getRegistrationStartAt());
        course.setRegistrationEndAt(request.getRegistrationEndAt());
        course.setAdditionalNotes(request.getAdditionalNotes());
        course.setAutoApproveEligible(Boolean.TRUE.equals(request.getAutoApproveEligible()));
        course.setScoreVisible(Boolean.TRUE.equals(request.getScoreVisible()));
        course.setAttendanceEnabled(Boolean.TRUE.equals(request.getAttendanceEnabled()));
        course.setAutoSendInvitation(Boolean.TRUE.equals(request.getAutoSendInvitation()));
        course.setStatus(Boolean.TRUE);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        course.setCreatedBy(auditActor);
        course.setUpdatedBy(auditActor);
        validateRegistrationWindow(course.getRegistrationStartAt(), course.getRegistrationEndAt());
        applyOwnerScope(course, courseType, authentication);
        course.setPassingScore(request.getPassingScore());
        courseScoreComponentService.validateInputs(request.getScoreComponents());
        requirePassingScoreWithinLimit(course, CourseScoreComponentService.maxFinalOfInputs(request.getScoreComponents()));
        applyRecipients(course, request.getRecipientUsernames());
        applyTargetDeaneries(course, request.getTargetDeaneryIds());
        applyOpenToOtherDeaneries(course, request.getOpenToOtherDeaneries());
        TrainingCourseStatusSupport.refresh(course, now);
        trainingCourseRepository.save(course);
        courseScoreComponentService.sync(course, request.getScoreComponents(), auditActor);
        if (Boolean.TRUE.equals(course.getOpenToOtherDeaneries()) && crossDeaneryAccessService != null) {
            crossDeaneryAccessService.notifyCourseOpened(course, auditActor);
        }
        return toResponse(trainingCourseRepository.findByCodeWithType(course.getCourseCode()).orElse(course));
    }

    @Cacheable(cacheNames = CacheNames.TRAINING_COURSE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    @Transactional(readOnly = true)
    public Page<TrainingCourseResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = SearchRequests.copy(request);
        // Tài khoản nhiều role xem được hợp các khóa: khóa giáo phận (quyền giáo phận), khóa giáo hạt (quyền giáo hạt)
        // và các khóa đang mở đăng ký trong giáo phận/giáo hạt của mình (quyền gửi đăng ký của giáo xứ).
        List<Map<String, Object>> groups = new ArrayList<>(accountScopeResolver.scopes(authentication, "training.course.read")
                .groups(Map.of(AccountScopeResolver.Level.DIOCESE, DIOCESE_FILTER_FIELD, AccountScopeResolver.Level.DEANERY, DEANERY_FILTER_FIELD)));
        boolean canSeeOpen = AuthorityChecks.hasAuthority(authentication, TrainingCourseAccessEvaluator.SUBMIT_PARISH);
        if (canSeeOpen) {
            groups.addAll(openCourseGroups(authentication));
        }
        if (groups.isEmpty()) {
            boolean anyAuthority = canSeeOpen
                    || AuthorityChecks.hasAuthority(authentication, TrainingCourseAccessEvaluator.READ_DIOCESE)
                    || AuthorityChecks.hasAuthority(authentication, TrainingCourseAccessEvaluator.READ_DEANERY);
            throw commonValidationHelper.forbidden(anyAuthority
                    ? "Tài khoản hiện tại không có phạm vi hợp lệ để xem khóa huấn luyện"
                    : "Tài khoản hiện tại không có quyền xem khóa huấn luyện");
        }
        scopedRequest.setScopeAnyOf(groups);

        Page<TrainingCourse> page = trainingCourseRepository.findAll(scopedRequest);
        // Số tham dự viên đã duyệt của cả trang trong một truy vấn.
        Map<String, Long> approvedCounts = courseParticipationRepository.countApprovedByCourseCodes(
                page.getContent().stream().map(TrainingCourse::getCourseCode).toList());
        Viewer viewer = viewerOf(authentication);
        List<TrainingCourseResponse> content = page.getContent().stream()
                .map(course -> forViewer(toResponse(course, null, approvedCounts.getOrDefault(course.getCourseCode(), 0L)), course, viewer))
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    // Khóa đang mở đăng ký (còn hoạt động) thuộc giáo phận hoặc giáo hạt của tài khoản.
    private List<Map<String, Object>> openCourseGroups(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        accountRepository.findActiveDioceseIdByUsername(username).ifPresent(id -> groups.add(openCourseGroup(DIOCESE_FILTER_FIELD, id)));
        accountRepository.findActiveDeaneryIdByUsername(username).ifPresent(id -> groups.add(openCourseGroup(DEANERY_FILTER_FIELD, id)));
        return groups;
    }

    private Map<String, Object> openCourseGroup(String ownerField, UUID ownerId) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put(ownerField, ownerId);
        group.put("status", Boolean.TRUE);
        group.put("registrationStatus", CourseRegistrationStatus.OPEN);
        return group;
    }

    // Khóa đang mở đăng ký trong phạm vi tài khoản (kể cả tài khoản giáo xứ, không cần quyền xem danh sách khóa).
    // senderType bỏ trống: hợp các khóa của mọi tư cách tài khoản có; có giá trị: chỉ khóa của tư cách đó.
    @Transactional(readOnly = true)
    public List<TrainingCourseResponse> findRegistrable(UnitType senderType, Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        List<UnitType> senders = senderType == null
                ? registrationSenderResolver.available(authentication)
                : List.of(registrationSenderResolver.resolve(authentication, senderType));
        if (senders.isEmpty()) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gửi đăng ký khóa huấn luyện");
        }
        // Giáo xứ và giáo hạt: khóa giáo phận của giáo phận mình + khóa giáo hạt của giáo hạt mình; giáo phận: chỉ khóa giáo phận của mình.
        boolean onlyDiocese = senders.stream().allMatch(sender -> sender == UnitType.DIOCESE);
        UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        UUID deaneryId = onlyDiocese ? null : accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
        if (dioceseId == null && deaneryId == null) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi hợp lệ để đăng ký khóa huấn luyện");
        }
        Viewer viewer = viewerOf(authentication);
        return trainingCourseRepository.findOpenForRegistration(dioceseId, deaneryId).stream()
                .map(course -> forViewer(toResponse(course), course, viewer))
                .toList();
    }

    // Các tư cách tài khoản có thể dùng để gửi đăng ký (giáo phận, giáo hạt, giáo xứ).
    public List<phucuong.ht.modules.training.RegistrationSenderResolver.Option> registrationSenders(Authentication authentication) {
        return registrationSenderResolver.options(authentication);
    }

    // Chi tiết khóa không qua cache (dùng ngay sau khi tạo/sửa để trả dữ liệu mới nhất).
    @Transactional(readOnly = true)
    public TrainingCourseResponse loadDetail(String courseCode) {
        return toResponse(trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode)),
                courseParticipationRepository.existsByCourseCode(courseCode));
    }

    @Cacheable(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #courseCode)")
    @Transactional(readOnly = true)
    public TrainingCourseResponse findById(String courseCode, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        return forViewer(toResponse(course, courseParticipationRepository.existsByCourseCode(courseCode)), course, viewerOf(authentication));
    }

    // Quyền cấp khóa (training.course.*) theo cấp và đơn vị của người xem: dùng để biết có thuộc đơn vị sở hữu khóa hay không.
    private record Viewer(boolean dioceseCourseRole, boolean deaneryCourseRole, UUID dioceseId, UUID deaneryId) {
    }

    private Viewer viewerOf(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (authentication == null || username == null || username.isBlank()) {
            return new Viewer(false, false, null, null);
        }
        boolean diocese = false;
        boolean deanery = false;
        for (org.springframework.security.core.GrantedAuthority granted : authentication.getAuthorities()) {
            String authority = granted.getAuthority();
            if (authority == null || !authority.startsWith("training.course.")) {
                continue;
            }
            diocese |= authority.endsWith(".diocese");
            deanery |= authority.endsWith(".deanery");
        }
        return new Viewer(diocese, deanery,
                diocese ? accountRepository.findActiveDioceseIdByUsername(username).orElse(null) : null,
                deanery ? accountRepository.findActiveDeaneryIdByUsername(username).orElse(null) : null);
    }

    // Điểm đạt và thành phần điểm chỉ dành cho đơn vị sở hữu khóa (giáo phận với khóa giáo phận, giáo hạt với khóa giáo hạt).
    private TrainingCourseResponse forViewer(TrainingCourseResponse response, TrainingCourse course, Viewer viewer) {
        boolean owns = trainingWorkflowSupport.isDeaneryManaged(course)
                ? viewer.deaneryCourseRole() && viewer.deaneryId() != null && course.getDeanery() != null && viewer.deaneryId().equals(course.getDeanery().getId())
                : viewer.dioceseCourseRole() && viewer.dioceseId() != null && course.getDiocese() != null && viewer.dioceseId().equals(course.getDiocese().getId());
        if (owns) {
            return response.toBuilder().viewerOwnsCourse(true).build();
        }
        return response.toBuilder().viewerOwnsCourse(false).passingScore(null).scoreComponents(null).build();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @EvictCaches.Certificates
    @Transactional
    public TrainingCourseResponse update(String courseCode, TrainingCourseUpdateRequest request, Authentication authentication) {
        TrainingCourse existing = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        courseActivityGuard.requireActive(existing);

        if (existing.isPast()) {
            throw commonValidationHelper.conflict("Khóa quá khứ được sửa tại chức năng riêng của khóa quá khứ");
        }
        TrainingCourseType currentType = existing.getCourseType();
        boolean typeChanging = request.getCourseTypeCode() != null
                && !request.getCourseTypeCode().isBlank()
                && !request.getCourseTypeCode().trim().equals(currentType.getTypeCode());
        TrainingCourseType targetType = typeChanging ? resolveCourseType(request.getCourseTypeCode(), authentication) : currentType;

        requireUpdateScope(authentication, existing, targetType);

        if (typeChanging && courseParticipationRepository.existsByCourseCode(courseCode)) {
            throw commonValidationHelper.conflict("Không thể đổi loại khóa huấn luyện sau khi đã có hồ sơ tham gia");
        }

        if (typeChanging) {
            existing.setCourseType(targetType);
        }
        if (request.getCourseName() != null) {
            existing.setCourseName(request.getCourseName());
        }
        if (request.getHostYear() != null) {
            if (!java.util.Objects.equals(existing.getHostYear(), request.getHostYear())) {
                requireNoParticipations(courseCode, "năm tổ chức");
            }
            existing.setHostYear(request.getHostYear());
        }
        if (request.getYearsActive() != null) {
            if (!java.util.Objects.equals(existing.getYearsActive() == null ? 0 : existing.getYearsActive(), request.getYearsActive())) {
                requireNoParticipations(courseCode, "số năm hoạt động");
            }
            existing.setYearsActive(request.getYearsActive());
        }
        if (request.getLocation() != null) {
            existing.setLocation(request.getLocation());
        }
        if (request.getStartDate() != null) {
            existing.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            existing.setEndDate(request.getEndDate());
        }
        if (request.getRegistrationStartAt() != null) {
            existing.setRegistrationStartAt(request.getRegistrationStartAt());
        }
        if (request.getRegistrationEndAt() != null) {
            existing.setRegistrationEndAt(request.getRegistrationEndAt());
        }
        if (request.getPassingScore() != null) {
            if (existing.getPassingScore() == null || existing.getPassingScore().compareTo(request.getPassingScore()) != 0) {
                requireNoParticipations(courseCode, "điểm đạt");
            }
            existing.setPassingScore(request.getPassingScore());
        }
        if (request.getAdditionalNotes() != null) {
            existing.setAdditionalNotes(request.getAdditionalNotes());
        }
        if (request.getAutoApproveEligible() != null) {
            existing.setAutoApproveEligible(request.getAutoApproveEligible());
        }
        if (request.getScoreVisible() != null) {
            existing.setScoreVisible(request.getScoreVisible());
        }
        if (request.getAttendanceEnabled() != null) {
            existing.setAttendanceEnabled(request.getAttendanceEnabled());
        }
        if (request.getAutoSendInvitation() != null) {
            existing.setAutoSendInvitation(request.getAutoSendInvitation());
        }
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện"));
        validateRegistrationWindow(existing.getRegistrationStartAt(), existing.getRegistrationEndAt());
        applyOwnerScope(existing, targetType, authentication);
        if (request.getScoreComponents() != null) {
            courseScoreComponentService.sync(existing, request.getScoreComponents(), existing.getUpdatedBy());
        }
        requirePassingScoreWithinLimit(existing, CourseScoreComponentService.maxFinalOfCourse(courseScoreComponentRepository.findByCourseCode(courseCode)));
        TrainingCourseStatusSupport.refresh(existing, OffsetDateTime.now());
        if (request.getRecipientUsernames() != null) {
            applyRecipients(existing, request.getRecipientUsernames());
        }
        applyTargetDeaneries(existing, request.getTargetDeaneryIds());
        boolean wasOpen = Boolean.TRUE.equals(existing.getOpenToOtherDeaneries());
        applyOpenToOtherDeaneries(existing, request.getOpenToOtherDeaneries());
        if (!wasOpen && Boolean.TRUE.equals(existing.getOpenToOtherDeaneries()) && crossDeaneryAccessService != null) {
            crossDeaneryAccessService.notifyCourseOpened(existing, existing.getUpdatedBy());
        }
        return toResponse(existing, courseParticipationRepository.existsByCourseCode(courseCode));
    }

    // Tác động của việc đổi trạng thái khóa, để cảnh báo trước khi xác nhận: vô hiệu hóa thì thu hồi các chứng nhận đã duyệt,
    // bật lại thì mở lại các chứng nhận bị thu hồi do vô hiệu hóa.
    @Transactional(readOnly = true)
    public CourseStatusImpactResponse statusImpact(String courseCode) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        return CourseStatusImpactResponse.builder()
                .active(Boolean.TRUE.equals(course.getStatus()))
                .approvedCertificates(certificateRepository.countByCourseAndStatus(courseCode, WorkflowApprovalStatus.APPROVED))
                .restorableCertificates(certificateRepository.countRevokedByCourseAndSource(courseCode, CertificateRevocationSource.COURSE_DISABLED))
                .build();
    }

    // Vô hiệu hóa / bật lại khóa. Phải gửi đúng trạng thái đích và chữ "Xác nhận"; BE kiểm tra lại cả hai.
    // Vô hiệu hóa: thu hồi mọi chứng nhận đã duyệt của khóa (lý do: khóa bị vô hiệu hóa), bỏ qua thứ tự cấp.
    // Bật lại: chỉ mở lại các chứng nhận bị thu hồi do vô hiệu hóa, chứng nhận thu hồi vì lý do khác giữ nguyên.
    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseResponse toggleStatus(String courseCode, CourseStatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        if (request.getTargetStatus() == null) {
            throw commonValidationHelper.validationError("Phải chỉ rõ trạng thái đích của khóa huấn luyện");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái khóa huấn luyện");
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        boolean current = Boolean.TRUE.equals(course.getStatus());
        boolean target = request.getTargetStatus();
        if (current == target) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã " + (target ? "được bật" : "bị vô hiệu hóa")
                    + " trước đó, vui lòng tải lại trang");
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Certificate> revokedCertificates = new ArrayList<>();
        List<Certificate> restoredCertificates = new ArrayList<>();
        if (!target) {
            for (Certificate certificate : certificateLifecycleService.approvedOf(courseCode)) {
                certificateLifecycleService.revoke(certificate, CertificateLifecycleService.COURSE_DISABLED_REASON,
                        CertificateRevocationSource.COURSE_DISABLED, false, actor, now);
                revokedCertificates.add(certificate);
            }
        }
        course.setStatus(target);
        course.setUpdatedAt(now);
        course.setUpdatedBy(actor);
        if (target) {
            for (Certificate certificate : certificateLifecycleService.revokedByCourseDisableOf(courseCode)) {
                certificateLifecycleService.restore(certificate, null, false, actor, now);
                restoredCertificates.add(certificate);
            }
        }
        if (trainingDecisionNotifier != null) {
            Map<String, String> reasons = new HashMap<>();
            revokedCertificates.forEach(certificate -> reasons.put(certificate.getCertificateCode(), CertificateLifecycleService.COURSE_DISABLED_REASON));
            trainingDecisionNotifier.notifyCertificates(Decision.CERTIFICATE_REVOKED, revokedCertificates, reasons, actor);
            trainingDecisionNotifier.notifyCertificates(Decision.CERTIFICATE_REISSUED, restoredCertificates, null, actor);
        }
        publishCourseStatusEvent(course, target, actor);
        return toResponse(course, courseParticipationRepository.existsByCourseCode(courseCode));
    }

    private void publishCourseStatusEvent(TrainingCourse course, boolean active, String actor) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.of(active ? "training.course.enabled" : "training.course.disabled", "trainingCourse", course.getCourseCode(), actor, Map.of("courseCode", course.getCourseCode())));
        certificateRealtimeNotifier.notifyChanged(active ? "COURSE_ENABLED" : "COURSE_DISABLED", course.getCourseCode(), null);
    }

    private String generateUniqueCourseCode() {
        String candidate;
        do {
            candidate = UUID.randomUUID().toString();
        } while (trainingCourseRepository.findByCodeWithType(candidate).isPresent());
        return candidate;
    }

    private TrainingCourseType resolveCourseType(String courseTypeCode, Authentication authentication) {
        if (courseTypeCode == null || courseTypeCode.isBlank()) {
            throw commonValidationHelper.validationError("courseTypeCode là bắt buộc");
        }
        TrainingCourseType courseType = trainingCourseTypeRepository.findByCodeWithPrerequisite(courseTypeCode.trim())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy loại khóa huấn luyện: " + courseTypeCode));
        // Loại khóa đi riêng theo giáo phận: khóa chỉ dùng loại khóa của giáo phận người thao tác.
        courseTypeDioceseGuard.requireSameDiocese(courseType, authentication);
        if (!Boolean.TRUE.equals(courseType.getStatus())) {
            throw commonValidationHelper.validationError("Loại khóa huấn luyện đang bị tạm ngưng: " + courseType.getTypeCode());
        }
        return courseType;
    }

    private void applyOwnerScope(TrainingCourse course, TrainingCourseType courseType, Authentication authentication) {
        if (trainingWorkflowSupport.isDeaneryManaged(courseType)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.CREATE_DEANERY);
            course.setDeanery(deaneryRepository.getRequired(deaneryId));
            course.setDiocese(null);
            return;
        }

        UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_DIOCESE);
        course.setDiocese(dioceseRepository.getRequired(dioceseId));
        course.setDeanery(null);
    }

    private void requireUpdateScope(Authentication authentication, TrainingCourse existing, TrainingCourseType targetCourseType) {
        UUID sourceDioceseId = sourceDioceseId(existing);
        if (trainingWorkflowSupport.isDeaneryManaged(existing)) {
            requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DEANERY);
            UUID activeDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(
                    authentication,
                    TrainingCourseAccessEvaluator.UPDATE_DEANERY
            );
            if (existing.getDeanery() == null || !activeDeaneryId.equals(existing.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Không thể cập nhật khóa huấn luyện ngoài giáo hạt hiện tại");
            }
        } else {
            requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DIOCESE);
            UUID activeDioceseId = accountScopeValidationHelper.requireActiveDioceseId(
                    authentication,
                    TrainingCourseAccessEvaluator.UPDATE_DIOCESE
            );
            if (existing.getDiocese() == null || !activeDioceseId.equals(existing.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Không thể cập nhật khóa huấn luyện ngoài giáo phận hiện tại");
            }
        }

        if (targetCourseType.getTypeCode().equals(existing.getCourseType().getTypeCode())) {
            return;
        }

        if (trainingWorkflowSupport.isDeaneryManaged(targetCourseType)) {
            requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DEANERY);
            UUID targetDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(
                    authentication,
                    TrainingCourseAccessEvaluator.UPDATE_DEANERY
            );
            Deanery targetDeanery = deaneryRepository.getRequired(targetDeaneryId);
            if (targetDeanery.getDiocese() == null || !sourceDioceseId.equals(targetDeanery.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Không thể chuyển khóa huấn luyện sang giáo hạt ngoài giáo phận sở hữu");
            }
            return;
        }

        requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DIOCESE);
        UUID targetDioceseId = accountScopeValidationHelper.requireActiveDioceseId(
                authentication,
                TrainingCourseAccessEvaluator.UPDATE_DIOCESE
        );
        if (!sourceDioceseId.equals(targetDioceseId)) {
            throw commonValidationHelper.forbidden("Không thể chuyển khóa huấn luyện sang giáo phận ngoài phạm vi sở hữu");
        }
    }

    private UUID sourceDioceseId(TrainingCourse course) {
        if (trainingWorkflowSupport.isDeaneryManaged(course)) {
            if (course.getDeanery() == null || course.getDeanery().getDiocese() == null) {
                throw commonValidationHelper.conflict("Khóa giáo hạt không có phạm vi giáo hạt / giáo phận hợp lệ");
            }
            return course.getDeanery().getDiocese().getId();
        }
        if (course.getDiocese() == null) {
            throw commonValidationHelper.conflict("Khóa huấn luyện không có phạm vi giáo phận hợp lệ");
        }
        return course.getDiocese().getId();
    }

    private void requireAuthority(Authentication authentication, String authority) {
        if (!securityUtils.hasAuthority(authentication, authority)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền cập nhật phạm vi khóa huấn luyện này");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseResponse setRegistrationOpen(String courseCode, CourseRegistrationToggleRequest request, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        courseActivityGuard.requireActive(course);
        OffsetDateTime now = OffsetDateTime.now();
        if (TrainingCourseStatusSupport.isCourseEnded(course, now)) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã kết thúc nên đăng ký phải đóng và không thể mở lại");
        }
        CourseRegistrationStatus current = TrainingCourseStatusSupport.registrationStatus(course, now);
        if (current == CourseRegistrationStatus.ENDED) {
            throw commonValidationHelper.conflict("Đã hết thời gian đăng ký, hãy gia hạn thời gian đăng ký trước khi mở lại");
        }
        if (current == CourseRegistrationStatus.NOT_STARTED) {
            throw commonValidationHelper.conflict("Chưa đến thời gian đăng ký, hệ thống sẽ tự mở khi đến giờ");
        }
        boolean open = Boolean.TRUE.equals(request.getOpen());
        CourseRegistrationStatus target = open ? CourseRegistrationStatus.OPEN : CourseRegistrationStatus.CLOSED;
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện");
        if (target != current) {
            course.setRegistrationStatus(target);
            course.setUpdatedAt(now);
            course.setUpdatedBy(actor);
            publishRegistrationEvent(course, open, "MANUAL", actor);
        }
        return toResponse(course);
    }

    // Cập nhật trạng thái đăng ký/tổ chức theo thời gian (bộ lập lịch gọi định kỳ): tự mở khi đến giờ, tự đóng khi hết hạn,
    // chuyển trạng thái tổ chức; phát sự kiện RabbitMQ khi đăng ký mở hoặc đóng tự động.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public int refreshCourseStatuses() {
        OffsetDateTime now = OffsetDateTime.now();
        int changedCourses = 0;
        for (TrainingCourse course : trainingCourseRepository.findCoursesWithOpenStatuses()) {
            CourseRegistrationStatus before = course.getRegistrationStatus();
            if (!TrainingCourseStatusSupport.refresh(course, now)) {
                continue;
            }
            changedCourses++;
            course.setUpdatedAt(now);
            course.setUpdatedBy(AUTO_ACTOR);
            CourseRegistrationStatus after = course.getRegistrationStatus();
            if (before == CourseRegistrationStatus.NOT_STARTED && after == CourseRegistrationStatus.OPEN) {
                publishRegistrationEvent(course, true, "AUTO", AUTO_ACTOR);
            } else if (before == CourseRegistrationStatus.OPEN && after == CourseRegistrationStatus.ENDED) {
                publishRegistrationEvent(course, false, "AUTO", AUTO_ACTOR);
            }
        }
        return changedCourses;
    }

    private void publishRegistrationEvent(TrainingCourse course, boolean open, String reason, String actor) {
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseCode", course.getCourseCode());
        payload.put("courseName", course.getCourseName());
        payload.put("open", open);
        payload.put("reason", reason);
        payload.put("deaneryManaged", deaneryManaged);
        payload.put("dioceseId", course.getDiocese() == null ? null : course.getDiocese().getId().toString());
        payload.put("deaneryId", course.getDeanery() == null ? null : course.getDeanery().getId().toString());
        domainEventPublisher.publishAfterCommit(DomainEventMessage.of(open ? EVENT_REGISTRATION_OPENED : EVENT_REGISTRATION_CLOSED, "trainingCourse", course.getCourseCode(), actor, payload));
    }

    // Tài khoản trong phạm vi hiện tại có quyền duyệt danh sách đăng ký của loại khóa được chọn.
    @Transactional(readOnly = true)
    public List<CourseRecipientCandidateResponse> recipientCandidates(String courseTypeCode, Authentication authentication) {
        TrainingCourseType courseType = resolveCourseType(courseTypeCode, authentication);
        boolean deanery = trainingWorkflowSupport.isDeaneryManaged(courseType);
        UUID scopeId = deanery
                ? accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.CREATE_DEANERY)
                : accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_DIOCESE);
        return notificationRecipientResolver.resolveRecipients(
                        null,
                        deanery ? CourseParticipationAccessEvaluator.APPROVE_DEANERY : CourseParticipationAccessEvaluator.APPROVE_DIOCESE,
                        deanery ? UnitType.DEANERY : UnitType.DIOCESE,
                        scopeId
                ).stream()
                .map(username -> CourseRecipientCandidateResponse.builder()
                        .username(username)
                        .fullName(accountRepository.findActiveByUsername(username)
                                .map(account -> LeaderNames.fullName(account.getLeader()))
                                .orElse(null))
                        .roleName(roleNames(username))
                        .build())
                .toList();
    }

    private void requirePassingScoreWithinLimit(TrainingCourse course, java.math.BigDecimal limit) {
        if (course.getPassingScore() == null || course.getCourseType() == null) {
            return;
        }
        if (limit != null && course.getPassingScore().compareTo(limit) > 0) {
            throw commonValidationHelper.validationError("Điểm đạt không được lớn hơn điểm tối đa của các thành phần điểm của khóa (" + limit.stripTrailingZeros().toPlainString() + ")");
        }
    }

    // Giáo hạt mà khóa giáo phận dành cho: null = giữ nguyên, danh sách (kể cả rỗng) = thay thế. Chỉ khóa giáo phận; mỗi giáo hạt phải
    // đang hoạt động và thuộc giáo phận của khóa. Khóa giáo hạt luôn không có danh sách này.
    private void applyTargetDeaneries(TrainingCourse course, List<UUID> requested) {
        if (trainingWorkflowSupport.isDeaneryManaged(course)) {
            if (requested != null && !requested.isEmpty()) {
                throw commonValidationHelper.validationError("Chỉ khóa giáo phận mới chọn được giáo hạt mà khóa dành cho");
            }
            course.getTargetDeaneryIds().clear();
            return;
        }
        if (requested == null) {
            return;
        }
        UUID dioceseId = course.getDiocese() == null ? null : course.getDiocese().getId();
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(requested.stream().filter(java.util.Objects::nonNull).toList());
        for (UUID deaneryId : ids) {
            Deanery deanery = deaneryRepository.findById(deaneryId)
                    .orElseThrow(() -> commonValidationHelper.validationError("Không tìm thấy giáo hạt " + deaneryId));
            if (deanery.getDiocese() == null || dioceseId == null || !dioceseId.equals(deanery.getDiocese().getId())) {
                throw commonValidationHelper.validationError("Giáo hạt " + deanery.getName() + " không thuộc giáo phận tổ chức khóa");
            }
            if (!Boolean.TRUE.equals(deanery.getStatus())) {
                throw commonValidationHelper.validationError("Giáo hạt " + deanery.getName() + " đang ngưng hoạt động");
            }
        }
        course.getTargetDeaneryIds().clear();
        course.getTargetDeaneryIds().addAll(ids);
    }

    // Mở khóa cho các giáo hạt khác: chỉ khóa giáo hạt. null = giữ nguyên; tắt thì giáo xứ các giáo hạt khác không gửi mới được nữa.
    private void applyOpenToOtherDeaneries(TrainingCourse course, Boolean requested) {
        if (!trainingWorkflowSupport.isDeaneryManaged(course)) {
            if (Boolean.TRUE.equals(requested)) {
                throw commonValidationHelper.validationError("Chỉ khóa giáo hạt mới mở đăng ký cho giáo hạt khác");
            }
            course.setOpenToOtherDeaneries(Boolean.FALSE);
            return;
        }
        if (requested != null) {
            course.setOpenToOtherDeaneries(requested);
        }
    }

    private List<TrainingCourseResponse.TargetDeanery> targetDeaneriesOf(TrainingCourse course) {
        return course.getTargetDeaneryIds().stream()
                .map(id -> new TrainingCourseResponse.TargetDeanery(id, deaneryRepository.findById(id).map(Deanery::getName).orElse(null)))
                .sorted(java.util.Comparator.comparing(item -> item.name() == null ? "" : item.name(), String::compareToIgnoreCase))
                .toList();
    }

    // Người nhận phải có quyền duyệt danh sách đăng ký trong đúng phạm vi sở hữu của khóa.
    private void applyRecipients(TrainingCourse course, List<String> requested) {
        List<String> usernames = requested == null ? List.<String>of() : requested.stream()
                .filter(username -> username != null && !username.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        course.getRecipientUsernames().clear();
        if (usernames.isEmpty()) {
            return;
        }
        boolean deanery = trainingWorkflowSupport.isDeaneryManaged(course);
        UUID scopeId = deanery ? course.getDeanery().getId() : course.getDiocese().getId();
        course.getRecipientUsernames().addAll(notificationRecipientResolver.resolveRecipients(
                usernames,
                deanery ? CourseParticipationAccessEvaluator.APPROVE_DEANERY : CourseParticipationAccessEvaluator.APPROVE_DIOCESE,
                deanery ? UnitType.DEANERY : UnitType.DIOCESE,
                scopeId
        ));
    }

    private String roleNames(String username) {
        String names = accountRoleRepository.findActiveByUsername(username).stream()
                .sorted(java.util.Comparator.comparing((phucuong.ht.modules.system.model.entity.AccountRole role) -> !Boolean.TRUE.equals(role.getIsPrimary())))
                .map(accountRole -> accountRole.getRole().getRoleName() == null || accountRole.getRole().getRoleName().isBlank()
                        ? accountRole.getRole().getRoleCode()
                        : accountRole.getRole().getRoleName())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return names.isBlank() ? null : names;
    }


    private void validateRegistrationWindow(OffsetDateTime registrationStartAt, OffsetDateTime registrationEndAt) {
        if (registrationStartAt == null || registrationEndAt == null) {
            return;
        }
        if (registrationEndAt.isBefore(registrationStartAt)) {
            throw commonValidationHelper.validationError("registrationEndAt không được nhỏ hơn registrationStartAt");
        }
    }

    // Kết thúc khóa thủ công: chốt kết quả chung, thăng cấp hồ sơ đạt và khóa điểm. Quyền kiểm tra ở controller.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseResponse finishCourse(String courseCode, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        courseActivityGuard.requireActive(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người kết thúc khóa");
        if (course.getFinishedAt() != null) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã được kết thúc");
        }
        if (course.getStartDate() == null || TrainingCourseStatusSupport.today().isBefore(course.getStartDate())) {
            throw commonValidationHelper.conflict("Khóa huấn luyện chưa diễn ra nên chưa thể kết thúc");
        }
        boolean registrationWasOpen = course.getRegistrationStatus() == CourseRegistrationStatus.OPEN;
        courseFinishService.finish(course, actor, OffsetDateTime.now());
        if (registrationWasOpen) {
            publishRegistrationEvent(course, false, "COURSE_FINISHED", actor);
        }
        publishCourseFinishedEvent(course, "MANUAL", actor);
        return toResponse(course, courseParticipationRepository.existsByCourseCode(courseCode));
    }

    private void publishCourseFinishedEvent(TrainingCourse course, String reason, String actor) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.of("training.course.finished", "trainingCourse", course.getCourseCode(), actor, Map.of("courseCode", course.getCourseCode(), "reason", reason)));
        certificateRealtimeNotifier.notifyChanged("COURSE_FINISHED", course.getCourseCode(), null);
    }

    // Khóa/mở chỉnh sửa điểm của khóa. Quyền kiểm tra ở controller (lock theo phạm vi sở hữu khóa).
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public TrainingCourseResponse setScoreLocked(String courseCode, boolean locked, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        courseActivityGuard.requireActive(course);
        if (!locked && course.getFinishedAt() != null) {
            throw commonValidationHelper.conflict("Khóa đã kết thúc nên không thể mở lại chỉnh sửa điểm");
        }
        course.setScoreLocked(locked);
        course.setUpdatedAt(OffsetDateTime.now());
        course.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người khóa/mở điểm"));
        return toResponse(course, courseParticipationRepository.existsByCourseCode(courseCode));
    }

    // Loại khóa, năm tổ chức, số năm hoạt động và điểm đạt bị khóa sau khi khóa đã có hồ sơ tham gia.
    private void requireNoParticipations(String courseCode, String fieldLabel) {
        if (courseParticipationRepository.existsByCourseCode(courseCode)) {
            throw commonValidationHelper.conflict("Không thể đổi " + fieldLabel + " sau khi đã có hồ sơ tham gia");
        }
    }

    private TrainingCourseResponse toResponse(TrainingCourse course) {
        return toResponse(course, null);
    }

    private TrainingCourseResponse toResponse(TrainingCourse course, Boolean hasParticipations) {
        return toResponse(course, hasParticipations,
                courseParticipationRepository.countApprovedByCourseCodes(List.of(course.getCourseCode())).getOrDefault(course.getCourseCode(), 0L));
    }

    private TrainingCourseResponse toResponse(TrainingCourse course, Boolean hasParticipations, long approvedParticipantCount) {
        TrainingCourseType courseType = course.getCourseType();
        return TrainingCourseResponse.builder()
                .courseCode(course.getCourseCode())
                .courseName(course.getCourseName())
                .courseTypeCode(courseType == null ? null : courseType.getTypeCode())
                .courseTypeName(courseType == null ? null : courseType.getTypeName())
                .managementUnitType(courseType != null && courseType.getManagementUnitType() != null ? courseType.getManagementUnitType()
                        : trainingWorkflowSupport.isDeaneryManaged(course) ? UnitType.DEANERY : UnitType.DIOCESE)
                .hostYear(course.getHostYear())
                .yearsActive(course.getYearsActive() == null ? 0 : course.getYearsActive())
                .hasParticipations(hasParticipations)
                .scoreComponents(hasParticipations == null ? null : courseScoreComponentService.list(course.getCourseCode()))
                .requiredCurrentLevelName(hasParticipations != null && courseType != null && courseType.getRequiredCurrentLevel() != null
                        ? leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course)).nameOf(courseType.getRequiredCurrentLevel()) : null)
                .requiredMinAge(hasParticipations != null && courseType != null ? courseType.getRequiredMinAge() : null)
                .prerequisiteTypeName(hasParticipations != null && courseType != null && courseType.getPrerequisiteType() != null
                        ? courseType.getPrerequisiteType().getTypeName() : null)
                .maxYearsAfterPrerequisite(hasParticipations != null && courseType != null ? courseType.getMaxYearsAfterPrerequisite() : null)
                .approvedParticipantCount(approvedParticipantCount)
                .scoreLocked(Boolean.TRUE.equals(course.getScoreLocked()))
                .autoApproveEligible(Boolean.TRUE.equals(course.getAutoApproveEligible()))
                .scoreVisible(Boolean.TRUE.equals(course.getScoreVisible()))
                .attendanceEnabled(Boolean.TRUE.equals(course.getAttendanceEnabled()))
                .autoSendInvitation(Boolean.TRUE.equals(course.getAutoSendInvitation()))
                .certificateRequired(trainingWorkflowSupport.requiresCertificate(course))
                .pastCourse(course.isPast())
                .resultingLevel(course.isPast() ? course.getResultingLevel() : null)
                .resultingLevelName(course.isPast() && course.getResultingLevel() != null
                        ? leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course)).nameOf(course.getResultingLevel()) : null)
                .finishedAt(course.getFinishedAt())
                .finishedBy(course.getFinishedBy())
                .finishedByName(auditActorNameResolver.resolve(course.getFinishedBy()))
                .location(course.getLocation())
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                .registrationStartAt(course.getRegistrationStartAt())
                .registrationEndAt(course.getRegistrationEndAt())
                .dioceseId(course.getDiocese() == null ? null : course.getDiocese().getId())
                .deaneryId(course.getDeanery() == null ? null : course.getDeanery().getId())
                .dioceseName(course.getDiocese() == null ? null : course.getDiocese().getName())
                .deaneryName(course.getDeanery() == null ? null : course.getDeanery().getName())
                .registrationOpen(course.getRegistrationStatus() == CourseRegistrationStatus.OPEN)
                .registrationStatus(course.getRegistrationStatus())
                .organizingStatus(course.getOrganizingStatus())
                .recipientUsernames(List.copyOf(course.getRecipientUsernames()))
                .targetDeaneryIds(List.copyOf(course.getTargetDeaneryIds()))
                .targetDeaneries(targetDeaneriesOf(course))
                .openToOtherDeaneries(Boolean.TRUE.equals(course.getOpenToOtherDeaneries()))
                .createdBy(course.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(course.getCreatedBy()))
                .updatedBy(course.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(course.getUpdatedBy()))
                .passingScore(course.getPassingScore())
                .additionalNotes(course.getAdditionalNotes())
                .status(course.getStatus())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
