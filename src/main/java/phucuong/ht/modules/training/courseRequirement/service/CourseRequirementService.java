package phucuong.ht.modules.training.courseRequirement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.courseRequirement.model.request.CourseRequirementCreateRequest;
import phucuong.ht.modules.training.courseRequirement.model.request.CourseRequirementUpdateRequest;
import phucuong.ht.modules.training.courseRequirement.model.response.CourseRequirementResponse;
import phucuong.ht.modules.training.courseRequirement.repository.CourseRequirementRepository;
import phucuong.ht.modules.training.courseRequirement.security.CourseRequirementAccessEvaluator;
import phucuong.ht.modules.training.enums.CourseType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseRequirementService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";

    private final CourseRequirementRepository courseRequirementRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_DETAIL, allEntries = true)
    })
    public CourseRequirementResponse create(CourseRequirementCreateRequest request, Authentication authentication) {
        if (request.getCourseType() == null) {
            throw commonValidationHelper.validationError("courseType là bắt buộc");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo requirement");
        CourseRequirement requirement = new CourseRequirement();
        requirement.setRequirementCode(request.getRequirementCode());
        requirement.setRequirementName(request.getRequirementName());
        requirement.setCourseType(request.getCourseType());
        requirement.setRequiredCurrentLevel(request.getRequiredCurrentLevel());
        requirement.setRequiredMinAge(request.getRequiredMinAge());
        requirement.setRequiredYearsActive(request.getRequiredYearsActive());
        requirement.setStatus(Boolean.TRUE);
        requirement.setCreatedBy(actor);
        requirement.setUpdatedBy(actor);
        applyOwnerScope(requirement, request.getCourseType(), authentication);
        courseRequirementRepository.save(requirement);
        return toResponse(requirement);
    }

    @Cacheable(cacheNames = CacheNames.COURSE_REQUIREMENT_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<CourseRequirementResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        if (hasAuthority(authentication, CourseRequirementAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseRequirementAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
        } else if (hasAuthority(authentication, CourseRequirementAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseRequirementAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem requirement");
        }

        Page<CourseRequirement> page = courseRequirementRepository.findAll(scopedRequest);
        List<CourseRequirementResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    @Cacheable(cacheNames = CacheNames.COURSE_REQUIREMENT_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #requirementCode)")
    public CourseRequirementResponse findById(String requirementCode, Authentication authentication) {
        return toResponse(courseRequirementRepository.findByCodeWithScope(requirementCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy requirement " + requirementCode)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_DETAIL, allEntries = true)
    })
    public CourseRequirementResponse update(String requirementCode, CourseRequirementUpdateRequest request, Authentication authentication) {
        CourseRequirement requirement = courseRequirementRepository.findByCodeWithScope(requirementCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy requirement " + requirementCode));
        CourseType courseType = request.getCourseType() == null ? requirement.getCourseType() : request.getCourseType();
        if (request.getRequirementName() != null) {
            requirement.setRequirementName(request.getRequirementName());
        }
        if (request.getCourseType() != null) {
            requirement.setCourseType(request.getCourseType());
        }
        if (request.getRequiredCurrentLevel() != null) {
            requirement.setRequiredCurrentLevel(request.getRequiredCurrentLevel());
        }
        if (request.getRequiredMinAge() != null) {
            requirement.setRequiredMinAge(request.getRequiredMinAge());
        }
        if (request.getRequiredYearsActive() != null) {
            requirement.setRequiredYearsActive(request.getRequiredYearsActive());
        }
        requirement.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật requirement"));
        applyOwnerScope(requirement, courseType, authentication);
        return toResponse(requirement);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_DETAIL, allEntries = true)
    })
    public void delete(String requirementCode) {
        courseRequirementRepository.delete(requirementCode);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.COURSE_REQUIREMENT_DETAIL, allEntries = true)
    })
    public CourseRequirementResponse toggleStatus(String requirementCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái requirement");
        courseRequirementRepository.toggleStatus(requirementCode, actor);
        return findById(requirementCode, authentication);
    }

    private void applyOwnerScope(CourseRequirement requirement, CourseType courseType, Authentication authentication) {
        if (trainingWorkflowSupport.isDeaneryManaged(courseType)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseRequirementAccessEvaluator.CREATE_DEANERY);
            requirement.setDeanery(deaneryRepository.getRequired(deaneryId));
            requirement.setDiocese(null);
            return;
        }

        UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, CourseRequirementAccessEvaluator.CREATE_DIOCESE);
        requirement.setDiocese(dioceseRepository.getRequired(dioceseId));
        requirement.setDeanery(null);
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

    private CourseRequirementResponse toResponse(CourseRequirement requirement) {
        return CourseRequirementResponse.builder()
                .requirementCode(requirement.getRequirementCode())
                .requirementName(requirement.getRequirementName())
                .courseType(requirement.getCourseType())
                .requiredCurrentLevel(requirement.getRequiredCurrentLevel())
                .requiredMinAge(requirement.getRequiredMinAge())
                .requiredYearsActive(requirement.getRequiredYearsActive())
                .dioceseId(requirement.getDiocese() == null ? null : requirement.getDiocese().getId())
                .deaneryId(requirement.getDeanery() == null ? null : requirement.getDeanery().getId())
                .status(requirement.getStatus())
                .createdAt(requirement.getCreatedAt())
                .updatedAt(requirement.getUpdatedAt())
                .build();
    }
}
