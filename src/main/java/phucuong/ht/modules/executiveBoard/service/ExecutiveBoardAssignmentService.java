package phucuong.ht.modules.executiveBoard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;
import phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentCreateRequest;
import phucuong.ht.modules.executiveBoard.model.response.ExecutiveBoardAssignmentResponse;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.executiveBoard.security.ExecutiveBoardAssignmentAccessEvaluator;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutiveBoardAssignmentService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";
    private static final String PARISH_FILTER_FIELD = "parish.id";
    private static final String UNIT_TYPE_FILTER_FIELD = "unitType";

    private final ExecutiveBoardAssignmentRepository assignmentRepository;
    private final LeaderRepository leaderRepository;
    private final PositionRepository positionRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;
    private final AuditActorNameResolver auditActorNameResolver;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EXECUTIVE_ASSIGNMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL, allEntries = true)
    })
    @Transactional
    public ExecutiveBoardAssignmentResponse create(ExecutiveBoardAssignmentCreateRequest request, Authentication authentication) {
        validateRequest(request.getUnitType(), request.getUnitId(), request.getLeaderId(), request.getPositionCode());
        validateDateRange(request.getStartDate(), request.getEndDate());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gán chức vụ");

        Leader leader = leaderRepository.findByIdWithParish(request.getLeaderId())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy Huynh Trưởng " + request.getLeaderId()));
        Position position = positionRepository.findById(request.getPositionCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + request.getPositionCode()));

        enforcePositionActive(position);
        enforceUnitType(position, request.getUnitType());
        ScopeData scopeData = resolveScopeData(request.getUnitType(), request.getUnitId());
        enforceTargetScope(authentication, request.getUnitType(), scopeData, "create");
        enforceLeaderMembership(leader, request.getUnitType(), request.getUnitId());
        enforceUniqueness(request.getUnitType(), request.getUnitId(), request.getPositionCode(), null);

        try {
            ExecutiveBoardAssignment assignment = new ExecutiveBoardAssignment();
            assignment.setUnitType(request.getUnitType());
            assignment.setUnitId(request.getUnitId());
            assignment.setLeader(leader);
            assignment.setPosition(position);
            assignment.setStartDate(request.getStartDate());
            assignment.setEndDate(request.getEndDate());
            assignment.setDiocese(scopeData.diocese());
            assignment.setDeanery(scopeData.deanery());
            assignment.setParish(scopeData.parish());
            assignment.setStatus(Boolean.TRUE);
            assignment.setCreatedAt(OffsetDateTime.now());
            assignment.setUpdatedAt(assignment.getCreatedAt());
            assignment.setCreatedBy(actor);
            assignment.setUpdatedBy(actor);
            assignmentRepository.save(assignment);
            return toDetailResponse(assignmentRepository.findByIdWithRelations(assignment.getId()).orElse(assignment));
        } catch (DataIntegrityViolationException exception) {
            throw commonValidationHelper.conflict("Chức vụ này đã được gán cho đơn vị hiện tại");
        }
    }

    @Cacheable(cacheNames = CacheNames.EXECUTIVE_ASSIGNMENT_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<ExecutiveBoardAssignmentResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        if (securityUtils.hasAuthority(authentication, ExecutiveBoardAssignmentAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, ExecutiveBoardAssignmentAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            scopedRequest.getFilters().put(UNIT_TYPE_FILTER_FIELD, UnitType.DIOCESE);
        } else if (securityUtils.hasAuthority(authentication, ExecutiveBoardAssignmentAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, ExecutiveBoardAssignmentAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
            scopedRequest.getFilters().put(UNIT_TYPE_FILTER_FIELD, UnitType.DEANERY);
        } else if (securityUtils.hasAuthority(authentication, ExecutiveBoardAssignmentAccessEvaluator.READ_PARISH)) {
            UUID parishId = accountScopeValidationHelper.requireActiveParishId(authentication, ExecutiveBoardAssignmentAccessEvaluator.READ_PARISH);
            scopedRequest.getFilters().put(PARISH_FILTER_FIELD, parishId);
            scopedRequest.getFilters().put(UNIT_TYPE_FILTER_FIELD, UnitType.PARISH);
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem phân công chức vụ");
        }

        Page<ExecutiveBoardAssignment> page = assignmentRepository.findAll(scopedRequest);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<UUID> ids = page.getContent().stream().map(ExecutiveBoardAssignment::getId).toList();
        Map<UUID, ExecutiveBoardAssignment> assignmentById = assignmentRepository.findAllByIdsWithRelations(ids).stream()
                .collect(Collectors.toMap(ExecutiveBoardAssignment::getId, Function.identity()));

        List<ExecutiveBoardAssignmentResponse> content = ids.stream()
                .map(assignmentById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    @Cacheable(cacheNames = CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public ExecutiveBoardAssignmentResponse findById(UUID id, Authentication authentication) {
        return toDetailResponse(assignmentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân công chức vụ " + id)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EXECUTIVE_ASSIGNMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL, allEntries = true)
    })
    @Transactional
    public ExecutiveBoardAssignmentResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái phân công chức vụ");
        ExecutiveBoardAssignment existing = assignmentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân công chức vụ " + id));
        if (!Boolean.TRUE.equals(existing.getStatus())) {
            // Bật lại phân công: chức vụ phải còn hoạt động và chưa có huynh trưởng khác giữ chức vụ này.
            enforcePositionActive(existing.getPosition());
            enforceUniqueness(existing.getUnitType(), existing.getUnitId(), existing.getPosition().getPositionCode(), id);
        }
        assignmentRepository.toggleStatus(id, actor);
        return toDetailResponse(assignmentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân công chức vụ " + id)));
    }

    private void validateRequest(UnitType unitType, UUID unitId, UUID leaderId, String positionCode) {
        if (unitType == null) {
            throw commonValidationHelper.validationError("unitType là bắt buộc");
        }
        commonValidationHelper.requireId(unitId, "unitId là bắt buộc");
        commonValidationHelper.requireId(leaderId, "leaderId là bắt buộc");
        if (positionCode == null || positionCode.isBlank()) {
            throw commonValidationHelper.validationError("positionCode là bắt buộc");
        }
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw commonValidationHelper.validationError("Ngày kết thúc không được trước ngày bắt đầu");
        }
    }

    private void enforcePositionActive(Position position) {
        if (!Boolean.TRUE.equals(position.getStatus())) {
            throw commonValidationHelper.validationError("Chức vụ đang bị vô hiệu hóa, không thể phân công");
        }
    }

    private void enforceUnitType(Position position, UnitType unitType) {
        if (position.getPositionType() != unitType) {
            throw commonValidationHelper.validationError("Chức vụ không thuộc đúng cấp đơn vị");
        }
    }

    private void enforceLeaderMembership(Leader leader, UnitType unitType, UUID unitId) {
        if (leader.getParish() == null) {
            throw commonValidationHelper.validationError("Huynh Trưởng chưa thuộc giáo xứ hợp lệ");
        }

        boolean matches = switch (unitType) {
            case DIOCESE -> leader.getParish().getDeanery() != null
                    && leader.getParish().getDeanery().getDiocese() != null
                    && unitId.equals(leader.getParish().getDeanery().getDiocese().getId());
            case DEANERY -> leader.getParish().getDeanery() != null
                    && unitId.equals(leader.getParish().getDeanery().getId());
            case PARISH -> unitId.equals(leader.getParish().getId());
        };

        if (!matches) {
            throw commonValidationHelper.forbidden("Huynh Trưởng không thuộc đúng đơn vị của chức vụ");
        }
    }

    // Chỉ được quản lý phân công đúng cấp mình có quyền, và trong đơn vị của chính mình.
    private void enforceTargetScope(Authentication authentication, UnitType unitType, ScopeData scopeData, String action) {
        String authority = "organization.executive_board_assignment." + action + "." + unitType.name().toLowerCase(java.util.Locale.ROOT);
        if (!securityUtils.hasAuthority(authentication, authority)) {
            throw commonValidationHelper.forbidden("Không có quyền " + action + " phân công cấp này");
        }
        switch (unitType) {
            case DIOCESE -> {
                if (!accountScopeValidationHelper.requireActiveDioceseId(authentication, authority).equals(scopeData.diocese().getId())) {
                    throw commonValidationHelper.forbidden("Không thể quản lý phân công ngoài giáo phận hiện tại");
                }
            }
            case DEANERY -> {
                if (!accountScopeValidationHelper.requireActiveDeaneryId(authentication, authority).equals(scopeData.deanery().getId())) {
                    throw commonValidationHelper.forbidden("Không thể quản lý phân công ngoài giáo hạt hiện tại");
                }
            }
            case PARISH -> {
                if (!accountScopeValidationHelper.requireActiveParishId(authentication, authority).equals(scopeData.parish().getId())) {
                    throw commonValidationHelper.forbidden("Không thể quản lý phân công ngoài giáo xứ hiện tại");
                }
            }
        }
    }

    private void enforceUniqueness(UnitType unitType, UUID unitId, String positionCode, UUID excludedId) {
        if (assignmentRepository.existsActiveCurrentAssignment(unitType, unitId, positionCode, excludedId)) {
            throw commonValidationHelper.conflict("Chức vụ này đang có người được phân công. Hãy vô hiệu hóa phân công hiện tại trước khi phân công người khác");
        }
    }

    private ScopeData resolveScopeData(UnitType unitType, UUID unitId) {
        return switch (unitType) {
            case DIOCESE -> {
                Diocese diocese = dioceseRepository.findById(unitId)
                        .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo phận " + unitId));
                yield new ScopeData(diocese, null, null);
            }
            case DEANERY -> {
                Deanery deanery = deaneryRepository.findByIdWithDiocese(unitId)
                        .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt " + unitId));
                yield new ScopeData(deanery.getDiocese(), deanery, null);
            }
            case PARISH -> {
                Parish parish = parishRepository.findByIdWithDeaneryAndDiocese(unitId)
                        .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ " + unitId));
                yield new ScopeData(parish.getDeanery().getDiocese(), parish.getDeanery(), parish);
            }
        };
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

    private ExecutiveBoardAssignmentResponse toResponse(ExecutiveBoardAssignment assignment) {
        return responseBuilder(assignment).build();
    }

    // Màn hình chi tiết hiển thị tên thay vì mã: đơn vị và người tạo/cập nhật.
    private ExecutiveBoardAssignmentResponse toDetailResponse(ExecutiveBoardAssignment assignment) {
        return responseBuilder(assignment)
                .unitName(resolveUnitName(assignment))
                .createdByName(auditActorNameResolver.resolve(assignment.getCreatedBy()))
                .updatedByName(auditActorNameResolver.resolve(assignment.getUpdatedBy()))
                .build();
    }

    private String resolveUnitName(ExecutiveBoardAssignment assignment) {
        if (assignment.getUnitType() == null) {
            return null;
        }
        return switch (assignment.getUnitType()) {
            case DIOCESE -> assignment.getDiocese() == null ? null : assignment.getDiocese().getName();
            case DEANERY -> assignment.getDeanery() == null ? null : assignment.getDeanery().getName();
            case PARISH -> assignment.getParish() == null ? null : assignment.getParish().getName();
        };
    }

    private ExecutiveBoardAssignmentResponse.ExecutiveBoardAssignmentResponseBuilder responseBuilder(ExecutiveBoardAssignment assignment) {
        return ExecutiveBoardAssignmentResponse.builder()
                .id(assignment.getId())
                .unitType(assignment.getUnitType())
                .unitId(assignment.getUnitId())
                .dioceseId(assignment.getDiocese() == null ? null : assignment.getDiocese().getId())
                .deaneryId(assignment.getDeanery() == null ? null : assignment.getDeanery().getId())
                .parishId(assignment.getParish() == null ? null : assignment.getParish().getId())
                .leaderId(assignment.getLeader() == null ? null : assignment.getLeader().getId())
                .leaderHolyName(assignment.getLeader() == null ? null : assignment.getLeader().getHolyName())
                .leaderFullName(assignment.getLeader() == null ? null : buildFullName(assignment.getLeader()))
                .leaderParishName(assignment.getLeader() == null || assignment.getLeader().getParish() == null ? null : assignment.getLeader().getParish().getName())
                .leaderDeaneryName(assignment.getLeader() == null || assignment.getLeader().getParish() == null || assignment.getLeader().getParish().getDeanery() == null
                        ? null : assignment.getLeader().getParish().getDeanery().getName())
                .leaderParishId(assignment.getLeader() == null || assignment.getLeader().getParish() == null ? null : assignment.getLeader().getParish().getId())
                .positionCode(assignment.getPosition() == null ? null : assignment.getPosition().getPositionCode())
                .positionName(assignment.getPosition() == null ? null : assignment.getPosition().getPositionName())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .status(assignment.getStatus())
                .createdAt(assignment.getCreatedAt())
                .createdBy(assignment.getCreatedBy())
                .updatedAt(assignment.getUpdatedAt())
                .updatedBy(assignment.getUpdatedBy());
    }

    private String buildFullName(Leader leader) {
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }

    private record ScopeData(Diocese diocese, Deanery deanery, Parish parish) {
    }
}
