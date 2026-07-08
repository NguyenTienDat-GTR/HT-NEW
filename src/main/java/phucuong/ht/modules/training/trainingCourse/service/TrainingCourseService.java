package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.courseRequirement.repository.CourseRequirementRepository;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseCreateRequest;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

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
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;

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
        course.setCourseType(request.getCourseType());
        course.setAdditionalNotes(request.getAdditionalNotes());
        course.setStatus(Boolean.TRUE);
        course.setCreatedBy(auditActor);
        course.setUpdatedBy(auditActor);
        applyOwnerScope(course, request.getCourseType(), authentication);
        trainingCourseRepository.save(course);
        return toResponse(trainingCourseRepository.findByCodeWithRequirement(course.getCourseCode()).orElse(course));
    }

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

    public TrainingCourseResponse findById(String courseCode) {
        return toResponse(trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode)));
    }

    public TrainingCourseResponse update(String courseCode, TrainingCourseUpdateRequest request, Authentication authentication) {
        TrainingCourse existing = trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        CourseType targetCourseType = request.getCourseType() == null ? existing.getCourseType() : request.getCourseType();
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
        if (request.getCourseType() != null) {
            existing.setCourseType(request.getCourseType());
        }
        if (request.getAdditionalNotes() != null) {
            existing.setAdditionalNotes(request.getAdditionalNotes());
        }
        existing.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện"));
        applyOwnerScope(existing, targetCourseType, authentication);
        return toResponse(existing);
    }

    public void delete(String courseCode) {
        trainingCourseRepository.delete(courseCode);
    }

    public TrainingCourseResponse toggleStatus(String courseCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái khóa huấn luyện");
        trainingCourseRepository.toggleStatus(courseCode, actor);
        return findById(courseCode);
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

    private CourseRequirement resolveRequirement(String requirementCode) {
        if (requirementCode == null || requirementCode.isBlank()) {
            return null;
        }
        return courseRequirementRepository.findByCodeWithScope(requirementCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy requirement " + requirementCode));
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

    private TrainingCourseResponse toResponse(TrainingCourse course) {
        return TrainingCourseResponse.builder()
                .courseCode(course.getCourseCode())
                .requirementCode(course.getRequirement() == null ? null : course.getRequirement().getRequirementCode())
                .courseName(course.getCourseName())
                .hostYear(course.getHostYear())
                .location(course.getLocation())
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                .courseType(course.getCourseType())
                .dioceseId(course.getDiocese() == null ? null : course.getDiocese().getId())
                .deaneryId(course.getDeanery() == null ? null : course.getDeanery().getId())
                .additionalNotes(course.getAdditionalNotes())
                .status(course.getStatus())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
