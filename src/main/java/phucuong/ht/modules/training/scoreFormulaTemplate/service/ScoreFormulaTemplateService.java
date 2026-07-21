package phucuong.ht.modules.training.scoreFormulaTemplate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingScoreFormulaEvaluator;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;
import phucuong.ht.modules.training.scoreComponentTemplate.repository.ScoreComponentTemplateRepository;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaComponent;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.request.ScoreFormulaTemplateCreateRequest;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.request.ScoreFormulaTemplateUpdateRequest;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.response.ScoreFormulaComponentResponse;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.response.ScoreFormulaTemplateResponse;
import phucuong.ht.modules.training.scoreFormulaTemplate.repository.ScoreFormulaTemplateRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScoreFormulaTemplateService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";

    private final ScoreFormulaTemplateRepository repository;
    private final ScoreComponentTemplateRepository scoreComponentTemplateRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final TrainingScoreFormulaEvaluator formulaEvaluator;

    public ScoreFormulaTemplateResponse create(ScoreFormulaTemplateCreateRequest request, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo công thức tính điểm");
        ScoreFormulaTemplate formula = new ScoreFormulaTemplate();
        formula.setCode(commonValidationHelper.normalizeRequiredText(request.getCode()));
        formula.setName(commonValidationHelper.normalizeRequiredText(request.getName()));
        formula.setExpression(commonValidationHelper.normalizeRequiredText(request.getExpression()));
        formula.setPassingScore(request.getPassingScore());
        formula.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        formula.setUnitType(request.getUnitType());
        formula.setStatus(Boolean.TRUE);
        formula.setCreatedBy(actor);
        formula.setUpdatedBy(actor);
        applyOwnerScope(formula, request.getUnitType(), authentication);
        replaceComponents(formula, request.getComponentIds(), actor);
        validateExpressionAgainstComponents(formula);
        repository.save(formula);
        return toResponse(formula);
    }

    public Page<ScoreFormulaTemplateResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        if (hasAuthority(authentication, "training.score_formula.read.diocese")) {
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.score_formula.read.diocese"));
        } else if (hasAuthority(authentication, "training.score_formula.read.deanery")) {
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.score_formula.read.deanery"));
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem công thức tính điểm");
        }
        Page<ScoreFormulaTemplate> page = repository.findAll(scopedRequest);
        List<ScoreFormulaTemplateResponse> content = page.getContent().stream()
                .map(formula -> repository.findByIdWithComponents(formula.getId()).orElse(formula))
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    public ScoreFormulaTemplateResponse findById(String id) {
        return toResponse(repository.findByIdWithComponents(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy công thức tính điểm: " + id)));
    }

    public ScoreFormulaTemplateResponse update(String id, ScoreFormulaTemplateUpdateRequest request, Authentication authentication) {
        if (repository.hasScoredParticipations(id)) {
            throw commonValidationHelper.conflict("Không thể sửa công thức tính điểm đã có dữ liệu chấm điểm");
        }
        ScoreFormulaTemplate formula = repository.findByIdWithComponents(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy công thức tính điểm: " + id));
        if (request.getCode() != null) {
            formula.setCode(commonValidationHelper.normalizeRequiredText(request.getCode()));
        }
        if (request.getName() != null) {
            formula.setName(commonValidationHelper.normalizeRequiredText(request.getName()));
        }
        if (request.getExpression() != null) {
            formula.setExpression(commonValidationHelper.normalizeRequiredText(request.getExpression()));
        }
        if (request.getPassingScore() != null) {
            formula.setPassingScore(request.getPassingScore());
        }
        if (request.getDescription() != null) {
            formula.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật công thức tính điểm");
        if (request.getComponentIds() != null) {
            replaceComponents(formula, request.getComponentIds(), actor);
        }
        formula.setUpdatedBy(actor);
        validateExpressionAgainstComponents(formula);
        return toResponse(formula);
    }

    public ScoreFormulaTemplateResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        repository.toggleStatus(id, commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái công thức tính điểm"));
        return findById(id);
    }

    private void applyOwnerScope(ScoreFormulaTemplate formula, UnitType unitType, Authentication authentication) {
        if (unitType == UnitType.DIOCESE) {
            formula.setDiocese(dioceseRepository.getRequired(accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.score_formula.create.diocese")));
            formula.setDeanery(null);
            return;
        }
        if (unitType == UnitType.DEANERY) {
            formula.setDeanery(deaneryRepository.getRequired(accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.score_formula.create.deanery")));
            formula.setDiocese(null);
            return;
        }
        throw commonValidationHelper.validationError("unitType của công thức tính điểm phải là DIOCESE hoặc DEANERY");
    }

    private void replaceComponents(ScoreFormulaTemplate formula, List<String> componentIds, String actor) {
        if (componentIds == null || componentIds.isEmpty()) {
            throw commonValidationHelper.validationError("componentIds là bắt buộc");
        }
        List<String> normalizedIds = new ArrayList<>(new LinkedHashSet<>(componentIds));
        List<ScoreComponentTemplate> components = scoreComponentTemplateRepository.findAllByIdsWithScope(normalizedIds);
        if (components.size() != normalizedIds.size()) {
            throw commonValidationHelper.validationError("Có một hoặc nhiều thành phần điểm không tồn tại");
        }
        Map<String, ScoreComponentTemplate> byId = new LinkedHashMap<>();
        components.forEach(component -> byId.put(component.getId(), component));

        formula.getComponents().clear();
        int order = 0;
        for (String componentId : normalizedIds) {
            ScoreComponentTemplate component = byId.get(componentId);
            validateComponentScope(formula, component);

            ScoreFormulaComponent link = new ScoreFormulaComponent();
            link.setFormula(formula);
            link.setComponent(component);
            link.setDisplayOrder(order++);
            link.setStatus(Boolean.TRUE);
            link.setCreatedBy(actor);
            link.setUpdatedBy(actor);
            formula.getComponents().add(link);
        }
    }

    private void validateComponentScope(ScoreFormulaTemplate formula, ScoreComponentTemplate component) {
        if (formula.getUnitType() != component.getUnitType()) {
            throw commonValidationHelper.validationError("unitType của công thức và thành phần điểm phải trùng nhau");
        }
        if (formula.getUnitType() == UnitType.DIOCESE) {
            if (formula.getDiocese() == null || component.getDiocese() == null
                    || !formula.getDiocese().getId().equals(component.getDiocese().getId())) {
                throw commonValidationHelper.validationError("Các thành phần điểm phải thuộc cùng một giáo phận với công thức");
            }
            return;
        }
        if (formula.getUnitType() == UnitType.DEANERY) {
            if (formula.getDeanery() == null || component.getDeanery() == null
                    || !formula.getDeanery().getId().equals(component.getDeanery().getId())) {
                throw commonValidationHelper.validationError("Các thành phần điểm phải thuộc cùng một giáo hạt với công thức");
            }
            return;
        }
        throw commonValidationHelper.validationError("unitType của công thức tính điểm không được hỗ trợ");
    }

    private void validateExpressionAgainstComponents(ScoreFormulaTemplate formula) {
        Set<String> variableNames = formulaEvaluator.extractVariables(formula.getExpression());
        Set<String> componentCodes = new LinkedHashSet<>();
        for (ScoreFormulaComponent componentLink : formula.getComponents()) {
            componentCodes.add(componentLink.getComponent().getCode());
        }
        if (!componentCodes.equals(variableNames)) {
            throw commonValidationHelper.validationError("Biến trong biểu thức phải khớp chính xác với mã các thành phần điểm đã chọn");
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

    private boolean hasAuthority(Authentication authentication, String permission) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    private ScoreFormulaTemplateResponse toResponse(ScoreFormulaTemplate formula) {
        return ScoreFormulaTemplateResponse.builder()
                .id(formula.getId())
                .code(formula.getCode())
                .name(formula.getName())
                .expression(formula.getExpression())
                .passingScore(formula.getPassingScore())
                .description(formula.getDescription())
                .unitType(formula.getUnitType())
                .dioceseId(formula.getDiocese() == null ? null : formula.getDiocese().getId())
                .deaneryId(formula.getDeanery() == null ? null : formula.getDeanery().getId())
                .locked(repository.hasScoredParticipations(formula.getId()))
                .status(formula.getStatus())
                .components(formula.getComponents().stream().map(component -> ScoreFormulaComponentResponse.builder()
                        .id(component.getId())
                        .componentId(component.getComponent() == null ? null : component.getComponent().getId())
                        .componentCode(component.getComponent() == null ? null : component.getComponent().getCode())
                        .componentName(component.getComponent() == null ? null : component.getComponent().getName())
                        .maxScore(component.getComponent() == null ? null : component.getComponent().getMaxScore())
                        .displayOrder(component.getDisplayOrder())
                        .build()).toList())
                .createdAt(formula.getCreatedAt())
                .updatedAt(formula.getUpdatedAt())
                .build();
    }
}
