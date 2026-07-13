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
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.courseRequirement.repository.CourseRequirementRepository;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;
import phucuong.ht.modules.training.scoreFormulaTemplate.repository.ScoreFormulaTemplateRepository;
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
    private final CourseRequirementRepository courseRequirementRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ScoreFormulaTemplateRepository scoreFormulaTemplateRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public TrainingCourseResponse create(TrainingCourseCreateRequest request, Authentication authentication) {
        if (request.getCourseType() == null) {
            throw commonValidationHelper.validationError("courseType là bắt buộc");
        }

        CourseRequirement requirement = resolveRequirement(request.getRequirementCode());
        String auditActor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo khóa huấn luyện");

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(request.getCourseCode());
        course.setRequirement(requirement);
        course.setCourseName(request.getCourseName());
        course.setHostYear(request.getHostYear());
        course.setLocation(request.getLocation());
        course.setStartDate(request.getStartDate());
        course.setEndDate(request.getEndDate());
        course.setRegistrationStartAt(request.getRegistrationStartAt());
        course.setRegistrationEndAt(request.getRegistrationEndAt());
        course.setCourseType(request.getCourseType());
        course.setAdditionalNotes(request.getAdditionalNotes());
        course.setStatus(Boolean.TRUE);
        course.setCreatedBy(auditActor);
        course.setUpdatedBy(auditActor);
        validateRegistrationWindow(course.getRegistrationStartAt(), course.getRegistrationEndAt());
        applyOwnerScope(course, request.getCourseType(), authentication);
        validateRequirementScope(requirement, course, request.getCourseType());
        applyScoreFormula(course, request.getScoreFormulaId(), authentication);
        trainingCourseRepository.save(course);
        return toResponse(trainingCourseRepository.findByCodeWithRequirement(course.getCourseCode()).orElse(course));
    }

    @Cacheable(cacheNames = CacheNames.TRAINING_COURSE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
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
    public TrainingCourseResponse findById(String courseCode, Authentication authentication) {
        return toResponse(trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public TrainingCourseResponse update(String courseCode, TrainingCourseUpdateRequest request, Authentication authentication) {
        TrainingCourse existing = trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        CourseType targetCourseType = request.getCourseType() == null ? existing.getCourseType() : request.getCourseType();
        requireUpdateScope(authentication, existing, targetCourseType);
        if (request.getCourseType() != null && request.getCourseType() != existing.getCourseType()
                && courseParticipationRepository.existsByCourseCode(courseCode)) {
            throw commonValidationHelper.conflict("Không thể đổi loại khóa học sau khi đã có hồ sơ tham gia");
        }
        if (request.getCourseType() != null && request.getCourseType() != existing.getCourseType()
                && (request.getScoreFormulaId() == null || request.getScoreFormulaId().isBlank())) {
            throw commonValidationHelper.validationError("Đổi courseType phải gửi scoreFormulaId phù hợp với scope mới");
        }
        if (request.getRequirementCode() != null) {
            existing.setRequirement(resolveRequirement(request.getRequirementCode()));
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
        if (request.getCourseType() != null) {
            existing.setCourseType(request.getCourseType());
        }
        if (request.getScoreFormulaId() != null) {
            applyScoreFormula(existing, request.getScoreFormulaId(), authentication);
        }
        if (request.getAdditionalNotes() != null) {
            existing.setAdditionalNotes(request.getAdditionalNotes());
        }
        existing.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện"));
        validateRegistrationWindow(existing.getRegistrationStartAt(), existing.getRegistrationEndAt());
        applyOwnerScope(existing, targetCourseType, authentication);
        validateRequirementScope(existing.getRequirement(), existing, targetCourseType);
        return toResponse(existing);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @Transactional
    public void delete(String courseCode) {
        trainingCourseRepository.delete(courseCode);
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

    private void applyOwnerScope(TrainingCourse course, CourseType courseType, Authentication authentication) {
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

    private void requireUpdateScope(Authentication authentication, TrainingCourse existing, CourseType targetCourseType) {
        UUID sourceDioceseId = sourceDioceseId(existing);
        if (trainingWorkflowSupport.isDeaneryManaged(existing.getCourseType())) {
            requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DEANERY);
            UUID activeDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(
                    authentication,
                    TrainingCourseAccessEvaluator.UPDATE_DEANERY
            );
            if (existing.getDeanery() == null || !activeDeaneryId.equals(existing.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Khong the cap nhat khoa huan luyen ngoai giao hat hien tai");
            }
        } else {
            requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DIOCESE);
            UUID activeDioceseId = accountScopeValidationHelper.requireActiveDioceseId(
                    authentication,
                    TrainingCourseAccessEvaluator.UPDATE_DIOCESE
            );
            if (existing.getDiocese() == null || !activeDioceseId.equals(existing.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Khong the cap nhat khoa huan luyen ngoai giao phan hien tai");
            }
        }

        if (targetCourseType == existing.getCourseType()) {
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
                throw commonValidationHelper.forbidden("Khong the chuyen khoa sang giao hat ngoai giao phan so huu");
            }
            return;
        }

        requireAuthority(authentication, TrainingCourseAccessEvaluator.UPDATE_DIOCESE);
        UUID targetDioceseId = accountScopeValidationHelper.requireActiveDioceseId(
                authentication,
                TrainingCourseAccessEvaluator.UPDATE_DIOCESE
        );
        if (!sourceDioceseId.equals(targetDioceseId)) {
            throw commonValidationHelper.forbidden("Khong the chuyen khoa sang giao phan ngoai scope so huu");
        }
    }

    private UUID sourceDioceseId(TrainingCourse course) {
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            if (course.getDeanery() == null || course.getDeanery().getDiocese() == null) {
                throw commonValidationHelper.conflict("Khoa DT khong co scope giao hat/giao phan hop le");
            }
            return course.getDeanery().getDiocese().getId();
        }
        if (course.getDiocese() == null) {
            throw commonValidationHelper.conflict("Khoa huan luyen khong co scope giao phan hop le");
        }
        return course.getDiocese().getId();
    }

    private void requireAuthority(Authentication authentication, String authority) {
        if (!securityUtils.hasAuthority(authentication, authority)) {
            throw commonValidationHelper.forbidden("Tai khoan hien tai khong co quyen cap nhat scope khoa huan luyen nay");
        }
    }

    private void applyScoreFormula(TrainingCourse course, String scoreFormulaId, Authentication authentication) {
        if (scoreFormulaId == null || scoreFormulaId.isBlank()) {
            throw commonValidationHelper.validationError("scoreFormulaId là bắt buộc");
        }
        ScoreFormulaTemplate formula = scoreFormulaTemplateRepository.findByIdWithComponents(scoreFormulaId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy công thức tính điểm: " + scoreFormulaId));
        if (course.getCourseType() == null) {
            throw commonValidationHelper.validationError("courseType là bắt buộc trước khi gán scoreFormula");
        }
        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.CREATE_DEANERY);
            if (formula.getDeanery() == null || !deaneryId.equals(formula.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Công thức tính điểm nằm ngoài phạm vi giáo hạt hiện tại");
            }
        } else {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_DIOCESE);
            if (formula.getDiocese() == null || !dioceseId.equals(formula.getDiocese().getId())) {
                throw commonValidationHelper.forbidden("Công thức tính điểm nằm ngoài phạm vi giáo phận hiện tại");
            }
        }
        course.setScoreFormula(formula);
    }

    private CourseRequirement resolveRequirement(String requirementCode) {
        if (requirementCode == null || requirementCode.isBlank()) {
            return null;
        }
        return courseRequirementRepository.findByCodeWithScope(requirementCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy yêu cầu khóa học " + requirementCode));
    }

    private void validateRequirementScope(CourseRequirement requirement, TrainingCourse course, CourseType courseType) {
        if (requirement == null) {
            return;
        }
        if (requirement.getCourseType() != courseType) {
            throw commonValidationHelper.validationError("Yêu cầu khóa học phải cùng courseType với khóa huấn luyện");
        }
        boolean validOwner = trainingWorkflowSupport.isDeaneryManaged(courseType)
                ? course.getDeanery() != null && requirement.getDeanery() != null && course.getDeanery().getId().equals(requirement.getDeanery().getId())
                : course.getDiocese() != null && requirement.getDiocese() != null && course.getDiocese().getId().equals(requirement.getDiocese().getId());
        if (!validOwner) {
            throw commonValidationHelper.validationError("Yêu cầu khóa học phải thuộc cùng scope quản lý với khóa huấn luyện");
        }
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
        return TrainingCourseResponse.builder()
                .courseCode(course.getCourseCode())
                .requirementCode(course.getRequirement() == null ? null : course.getRequirement().getRequirementCode())
                .courseName(course.getCourseName())
                .hostYear(course.getHostYear())
                .location(course.getLocation())
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                .registrationStartAt(course.getRegistrationStartAt())
                .registrationEndAt(course.getRegistrationEndAt())
                .courseType(course.getCourseType())
                .dioceseId(course.getDiocese() == null ? null : course.getDiocese().getId())
                .deaneryId(course.getDeanery() == null ? null : course.getDeanery().getId())
                .scoreFormulaId(course.getScoreFormula() == null ? null : course.getScoreFormula().getId())
                .additionalNotes(course.getAdditionalNotes())
                .status(course.getStatus())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
