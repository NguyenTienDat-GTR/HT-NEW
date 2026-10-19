package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;
import phucuong.ht.modules.training.scoreComponentTemplate.repository.ScoreComponentTemplateRepository;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.CoursePassingScoreLimitResponse;
import phucuong.ht.modules.training.trainingCourse.model.response.CourseRecipientCandidateResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseCreateRequest;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingCourseService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";

    private final TrainingCourseRepository trainingCourseRepository;
    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;
    private final ScoreComponentTemplateRepository scoreComponentTemplateRepository;
    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final AccountRepository accountRepository;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public TrainingCourseResponse create(TrainingCourseCreateRequest request, Authentication authentication) {
        TrainingCourseType courseType = resolveCourseType(request.getCourseTypeCode());
        String auditActor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo khóa huấn luyện");
        OffsetDateTime now = OffsetDateTime.now();

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(generateUniqueCourseCode());
        course.setCourseType(courseType);
        course.setCourseName(request.getCourseName());
        course.setHostYear(request.getHostYear());
        course.setLocation(request.getLocation());
        course.setStartDate(request.getStartDate());
        course.setEndDate(request.getEndDate());
        course.setRegistrationStartAt(request.getRegistrationStartAt());
        course.setRegistrationEndAt(request.getRegistrationEndAt());
        course.setAdditionalNotes(request.getAdditionalNotes());
        course.setStatus(Boolean.TRUE);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        course.setCreatedBy(auditActor);
        course.setUpdatedBy(auditActor);
        validateRegistrationWindow(course.getRegistrationStartAt(), course.getRegistrationEndAt());
        applyOwnerScope(course, courseType, authentication);
        course.setPassingScore(request.getPassingScore());
        requirePassingScoreWithinLimit(course);
        applyRecipients(course, request.getRecipientUsernames());
        trainingCourseRepository.save(course);
        return toResponse(trainingCourseRepository.findByCodeWithType(course.getCourseCode()).orElse(course));
    }

    @Cacheable(cacheNames = CacheNames.TRAINING_COURSE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    @Transactional(readOnly = true)
    public Page<TrainingCourseResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        if (hasAuthority(authentication, TrainingCourseAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
        } else if (hasAuthority(authentication, TrainingCourseAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem khóa huấn luyện");
        }

        Page<TrainingCourse> page = trainingCourseRepository.findAll(scopedRequest);
        List<TrainingCourseResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    @Cacheable(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #courseCode)")
    @Transactional(readOnly = true)
    public TrainingCourseResponse findById(String courseCode, Authentication authentication) {
        return toResponse(trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public TrainingCourseResponse update(String courseCode, TrainingCourseUpdateRequest request, Authentication authentication) {
        TrainingCourse existing = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));

        TrainingCourseType currentType = existing.getCourseType();
        boolean typeChanging = request.getCourseTypeCode() != null
                && !request.getCourseTypeCode().isBlank()
                && !request.getCourseTypeCode().trim().equals(currentType.getTypeCode());
        TrainingCourseType targetType = typeChanging ? resolveCourseType(request.getCourseTypeCode()) : currentType;

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
            existing.setHostYear(request.getHostYear());
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
            if (courseParticipationRepository.existsByCourseCode(courseCode)) {
                throw commonValidationHelper.conflict("Không thể đổi điểm đạt sau khi đã có hồ sơ tham gia");
            }
            existing.setPassingScore(request.getPassingScore());
        }
        if (request.getAdditionalNotes() != null) {
            existing.setAdditionalNotes(request.getAdditionalNotes());
        }
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện"));
        validateRegistrationWindow(existing.getRegistrationStartAt(), existing.getRegistrationEndAt());
        applyOwnerScope(existing, targetType, authentication);
        requirePassingScoreWithinLimit(existing);
        if (request.getRecipientUsernames() != null) {
            applyRecipients(existing, request.getRecipientUsernames());
        }
        return toResponse(existing);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public TrainingCourseResponse toggleStatus(String courseCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái khóa huấn luyện");
        trainingCourseRepository.toggleStatus(courseCode, actor);
        return findById(courseCode, authentication);
    }

    private String generateUniqueCourseCode() {
        String candidate;
        do {
            candidate = UUID.randomUUID().toString();
        } while (trainingCourseRepository.findByCodeWithType(candidate).isPresent());
        return candidate;
    }

    private TrainingCourseType resolveCourseType(String courseTypeCode) {
        if (courseTypeCode == null || courseTypeCode.isBlank()) {
            throw commonValidationHelper.validationError("courseTypeCode là bắt buộc");
        }
        TrainingCourseType courseType = trainingCourseTypeRepository.findByCodeWithPrerequisite(courseTypeCode.trim())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy loại khóa huấn luyện: " + courseTypeCode));
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
        if (trainingWorkflowSupport.isDeaneryManaged(existing.getCourseType())) {
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
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
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
    @Transactional
    public TrainingCourseResponse setRegistrationOpen(String courseCode, CourseRegistrationToggleRequest request, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        OffsetDateTime now = OffsetDateTime.now();
        if (course.getRegistrationEndAt() == null || now.isAfter(course.getRegistrationEndAt())) {
            throw commonValidationHelper.conflict("Đã hết thời gian đăng ký, hãy gia hạn thời gian đăng ký trước khi mở lại");
        }
        course.setRegistrationOpen(Boolean.TRUE.equals(request.getOpen()));
        course.setUpdatedAt(now);
        course.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện"));
        return toResponse(course);
    }

    // Tự đóng đăng ký các khóa đã hết thời gian đăng ký (gọi định kỳ bởi bộ lập lịch).
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    public int closeExpiredRegistrations() {
        return trainingCourseRepository.closeExpiredRegistrations(OffsetDateTime.now());
    }

    // Tài khoản trong phạm vi hiện tại có quyền duyệt danh sách đăng ký của loại khóa được chọn.
    @Transactional(readOnly = true)
    public List<CourseRecipientCandidateResponse> recipientCandidates(String courseTypeCode, Authentication authentication) {
        TrainingCourseType courseType = resolveCourseType(courseTypeCode);
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
                                .map(account -> fullName(account.getLeader()))
                                .orElse(null))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public CoursePassingScoreLimitResponse passingScoreLimit(String courseTypeCode, Authentication authentication) {
        TrainingCourseType courseType = resolveCourseType(courseTypeCode);
        boolean deanery = trainingWorkflowSupport.isDeaneryManaged(courseType);
        UUID dioceseId = null;
        UUID deaneryId = null;
        if (deanery) {
            deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.CREATE_DEANERY);
        } else {
            dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_DIOCESE);
        }
        List<ScoreComponentTemplate> components = scoreComponentTemplateRepository.findActiveForCourse(courseType.getTypeCode(), dioceseId, deaneryId);
        return CoursePassingScoreLimitResponse.builder()
                .maxPassingScore(maxFinalScore(components))
                .componentCount(components.size())
                .build();
    }

    // Điểm tổng kết tối đa = trung bình có hệ số của điểm tối đa các thành phần được tính vào điểm tổng kết.
    private java.math.BigDecimal maxFinalScore(List<ScoreComponentTemplate> components) {
        java.math.BigDecimal weighted = java.math.BigDecimal.ZERO;
        java.math.BigDecimal coefficients = java.math.BigDecimal.ZERO;
        for (ScoreComponentTemplate component : components) {
            if (Boolean.TRUE.equals(component.getIncludeInFinal())) {
                weighted = weighted.add(component.getMaxScore().multiply(component.getCoefficient()));
                coefficients = coefficients.add(component.getCoefficient());
            }
        }
        if (coefficients.signum() <= 0) {
            return null;
        }
        return weighted.divide(coefficients, 2, java.math.RoundingMode.HALF_UP);
    }

    private void requirePassingScoreWithinLimit(TrainingCourse course) {
        if (course.getPassingScore() == null || course.getCourseType() == null) {
            return;
        }
        java.math.BigDecimal limit = maxFinalScore(scoreComponentTemplateRepository.findActiveForCourse(
                course.getCourseType().getTypeCode(),
                course.getDiocese() == null ? null : course.getDiocese().getId(),
                course.getDeanery() == null ? null : course.getDeanery().getId()));
        if (limit != null && course.getPassingScore().compareTo(limit) > 0) {
            throw commonValidationHelper.validationError("Điểm đạt không được lớn hơn điểm tối đa của loại khóa (" + limit.stripTrailingZeros().toPlainString() + ")");
        }
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
        boolean deanery = trainingWorkflowSupport.isDeaneryManaged(course.getCourseType());
        UUID scopeId = deanery ? course.getDeanery().getId() : course.getDiocese().getId();
        course.getRecipientUsernames().addAll(notificationRecipientResolver.resolveRecipients(
                usernames,
                deanery ? CourseParticipationAccessEvaluator.APPROVE_DEANERY : CourseParticipationAccessEvaluator.APPROVE_DIOCESE,
                deanery ? UnitType.DEANERY : UnitType.DIOCESE,
                scopeId
        ));
    }

    private String fullName(phucuong.ht.modules.leader.model.entity.Leader leader) {
        if (leader == null) {
            return null;
        }
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
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

    private void validateRegistrationWindow(OffsetDateTime registrationStartAt, OffsetDateTime registrationEndAt) {
        if (registrationStartAt == null || registrationEndAt == null) {
            return;
        }
        if (registrationEndAt.isBefore(registrationStartAt)) {
            throw commonValidationHelper.validationError("registrationEndAt không được nhỏ hơn registrationStartAt");
        }
    }

    private TrainingCourseResponse toResponse(TrainingCourse course) {
        TrainingCourseType courseType = course.getCourseType();
        return TrainingCourseResponse.builder()
                .courseCode(course.getCourseCode())
                .courseName(course.getCourseName())
                .courseTypeCode(courseType == null ? null : courseType.getTypeCode())
                .courseTypeName(courseType == null ? null : courseType.getTypeName())
                .managementUnitType(courseType == null ? null : courseType.getManagementUnitType())
                .hostYear(course.getHostYear())
                .location(course.getLocation())
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                .registrationStartAt(course.getRegistrationStartAt())
                .registrationEndAt(course.getRegistrationEndAt())
                .dioceseId(course.getDiocese() == null ? null : course.getDiocese().getId())
                .deaneryId(course.getDeanery() == null ? null : course.getDeanery().getId())
                .dioceseName(course.getDiocese() == null ? null : course.getDiocese().getName())
                .deaneryName(course.getDeanery() == null ? null : course.getDeanery().getName())
                .registrationOpen(course.getRegistrationOpen())
                .recipientUsernames(List.copyOf(course.getRecipientUsernames()))
                .createdBy(course.getCreatedBy())
                .updatedBy(course.getUpdatedBy())
                .passingScore(course.getPassingScore())
                .additionalNotes(course.getAdditionalNotes())
                .status(course.getStatus())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
