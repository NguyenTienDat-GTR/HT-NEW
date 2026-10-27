package phucuong.ht.modules.training.scoreComponentTemplate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;
import phucuong.ht.modules.training.scoreComponentTemplate.model.request.ScoreComponentTemplateCreateRequest;
import phucuong.ht.modules.training.scoreComponentTemplate.model.request.ScoreComponentTemplateUpdateRequest;
import phucuong.ht.modules.training.scoreComponentTemplate.model.response.ScoreComponentTemplateResponse;
import phucuong.ht.modules.training.scoreComponentTemplate.repository.ScoreComponentTemplateRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScoreComponentTemplateService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";

    private final ScoreComponentTemplateRepository repository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final CourseTypeDioceseGuard courseTypeDioceseGuard;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

    @Transactional
    public ScoreComponentTemplateResponse create(ScoreComponentTemplateCreateRequest request, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo thành phần điểm");
        OffsetDateTime now = OffsetDateTime.now();
        ScoreComponentTemplate component = new ScoreComponentTemplate();
        component.setCode(java.util.UUID.randomUUID().toString());
        component.setName(commonValidationHelper.normalizeRequiredText(request.getName()));
        component.setMaxScore(request.getMaxScore());
        component.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        component.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType courseType = trainingCourseTypeRepository
                .findByCodeWithPrerequisite(request.getCourseTypeCode().trim())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy loại khóa " + request.getCourseTypeCode()));
        courseTypeDioceseGuard.requireSameDiocese(courseType, authentication);
        component.setUnitType(courseType.getManagementUnitType());
        component.setCourseType(courseType);
        component.setCoefficient(request.getCoefficient());
        component.setIncludeInFinal(request.getIncludeInFinal() == null ? Boolean.TRUE : request.getIncludeInFinal());
        component.setStatus(Boolean.TRUE);
        component.setCreatedAt(now);
        component.setUpdatedAt(now);
        component.setCreatedBy(actor);
        component.setUpdatedBy(actor);
        applyOwnerScope(component, courseType.getManagementUnitType(), authentication);
        repository.save(component);
        return toResponse(component);
    }

    @Transactional(readOnly = true)
    public Page<ScoreComponentTemplateResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        if (hasAuthority(authentication, "training.score_component.read.diocese")) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.score_component.read.diocese");
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
        } else if (hasAuthority(authentication, "training.score_component.read.deanery")) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.score_component.read.deanery");
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem thành phần điểm");
        }
        Page<ScoreComponentTemplate> page = repository.findAll(scopedRequest);
        java.util.Set<String> scored = repository.findIdsWithEnteredScores(page.getContent().stream().map(ScoreComponentTemplate::getId).toList());
        return new PageImpl<>(page.getContent().stream().map(component -> toResponse(component, scored.contains(component.getId()))).toList(), page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ScoreComponentTemplateResponse findById(String id) {
        return toResponse(repository.findByIdWithScope(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy thành phần điểm: " + id)));
    }

    @Transactional
    public ScoreComponentTemplateResponse update(String id, ScoreComponentTemplateUpdateRequest request, Authentication authentication) {
        ScoreComponentTemplate component = repository.findByIdWithScope(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy thành phần điểm: " + id));
        if (request.getName() != null) {
            component.setName(commonValidationHelper.normalizeRequiredText(request.getName()));
        }
        if (request.getMaxScore() != null) {
            component.setMaxScore(request.getMaxScore());
        }
        if (request.getDescription() != null) {
            component.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        if (request.getCoefficient() != null) {
            component.setCoefficient(request.getCoefficient());
        }
        if (request.getIncludeInFinal() != null) {
            component.setIncludeInFinal(request.getIncludeInFinal());
        }
        if (request.getDisplayOrder() != null) {
            component.setDisplayOrder(request.getDisplayOrder());
        }
        component.setUpdatedAt(OffsetDateTime.now());
        component.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật thành phần điểm"));
        return toResponse(component);
    }

    public ScoreComponentTemplateResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        ScoreComponentTemplate current = repository.findByIdWithScope(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy thành phần điểm: " + id));
        // Thành phần đã có điểm nhập vào thì không được tắt, để không làm lệch điểm tổng kết của các khóa đã chấm.
        if (Boolean.TRUE.equals(current.getStatus()) && !repository.findIdsWithEnteredScores(List.of(id)).isEmpty()) {
            throw commonValidationHelper.conflict("Thành phần điểm đã có điểm được nhập, không thể vô hiệu hóa");
        }
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        repository.toggleStatus(id, commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái thành phần điểm"));
        return findById(id);
    }

    private void applyOwnerScope(ScoreComponentTemplate component, UnitType unitType, Authentication authentication) {
        if (unitType == UnitType.DIOCESE) {
            component.setDiocese(dioceseRepository.getRequired(accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.score_component.create.diocese")));
            component.setDeanery(null);
            return;
        }
        if (unitType == UnitType.DEANERY) {
            component.setDeanery(deaneryRepository.getRequired(accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.score_component.create.deanery")));
            component.setDiocese(null);
            return;
        }
        throw commonValidationHelper.validationError("unitType của thành phần điểm phải là DIOCESE hoặc DEANERY");
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

    private boolean hasAuthority(Authentication authentication, String permission) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    private ScoreComponentTemplateResponse toResponse(ScoreComponentTemplate component) {
        return toResponse(component, !repository.findIdsWithEnteredScores(List.of(component.getId())).isEmpty());
    }

    private ScoreComponentTemplateResponse toResponse(ScoreComponentTemplate component, boolean hasScores) {
        return ScoreComponentTemplateResponse.builder()
                .id(component.getId())
                .code(component.getCode())
                .name(component.getName())
                .maxScore(component.getMaxScore())
                .description(component.getDescription())
                .displayOrder(component.getDisplayOrder())
                .courseTypeCode(component.getCourseType() == null ? null : component.getCourseType().getTypeCode())
                .courseTypeName(component.getCourseType() == null ? null : component.getCourseType().getTypeName())
                .coefficient(component.getCoefficient())
                .includeInFinal(component.getIncludeInFinal())
                .unitType(component.getUnitType())
                .dioceseId(component.getDiocese() == null ? null : component.getDiocese().getId())
                .deaneryId(component.getDeanery() == null ? null : component.getDeanery().getId())
                .status(component.getStatus())
                .hasScores(hasScores)
                .createdAt(component.getCreatedAt())
                .createdBy(component.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(component.getCreatedBy()))
                .updatedAt(component.getUpdatedAt())
                .updatedBy(component.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(component.getUpdatedBy()))
                .build();
    }
}
