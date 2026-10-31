package phucuong.ht.modules.parish.service;

import phucuong.ht.config.EvictCaches;
import phucuong.ht.common.security.UnitToggleSupport;
import phucuong.ht.common.repository.SearchRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.security.UnitStatusAuditService;
import phucuong.ht.common.dto.UnitStatusHistoryResponse;
import phucuong.ht.common.dto.UnitToggleImpactResponse;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.config.CacheNames;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.parish.helper.ParishScopeHelper;
import phucuong.ht.modules.parish.helper.ParishValidationHelper;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishParentChangeRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;
import phucuong.ht.modules.parish.model.response.ParishResponse;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.parish.security.ParishAccessEvaluator;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.notification.service.ToggleNotificationRecipientResolver;
import phucuong.ht.modules.system.service.RbacAuthRefreshNotifier;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParishService {

    private static final String DIOCESE_FILTER_FIELD = "deanery.diocese.id";
    private static final String DEANERY_FILTER_FIELD = "deanery.id";
    private static final String PARISH_FILTER_FIELD = "id";

    private final ParishRepository parishRepository;
    private final ParishValidationHelper parishValidationHelper;
    private final ParishScopeHelper parishScopeHelper;
    private final securityUtils securityUtils;
    private final AuditActorNameResolver auditActorNameResolver;
    @Autowired(required = false)
    private UnitToggleSupport toggleSupport = UnitToggleSupport.noop();

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse create(ParishCreateRequest request, Authentication authentication) {
        parishValidationHelper.validateCreateInput(request);
        parishScopeHelper.enforceCreateScope(request, authentication);
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        try {
            Deanery deanery = parishValidationHelper.requireExistingDeanery(request.getDeaneryId());
            toggleSupport.requireOperational(UnitType.DEANERY, deanery.getId());
            parishValidationHelper.validateCreateNameUniqueness(request.getName(), deanery.getId());
            ParishUnitType unitType = request.getUnitType() == null ? ParishUnitType.PARISH : request.getUnitType();
            Parish parent = parishValidationHelper.resolveParent(request, unitType, deanery.getId());
            if (parent != null) {
                toggleSupport.requireOperational(UnitType.PARISH, parent.getId());
            }
            Parish parish = new Parish();
            parish.setUnitType(unitType);
            parish.setParentParish(parent);
            parish.setDeanery(deanery);
            parish.setName(request.getName());
            parish.setPatronSaintDay(request.getPatronSaintDay());
            parish.setEstablishmentDate(request.getEstablishmentDate());
            parish.setChaplain(request.getChaplain());
            parish.setPhoneNumber(request.getPhoneNumber());
            parish.setEmail(request.getEmail());
            parish.setChapterName(request.getChapterName());
            parish.setStatus(Boolean.TRUE);
            parish.setCreatedAt(OffsetDateTime.now());
            parish.setUpdatedAt(parish.getCreatedAt());
            parish.setCreatedBy(auditActor);
            parish.setUpdatedBy(auditActor);
            parishRepository.save(parish);
            return toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(parish.getId()));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo xứ bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.PARISH_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<ParishResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = SearchRequests.copy(request);

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_ALL)) {
            return loadParishPageWithDeanery(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = parishScopeHelper.getRequiredActiveDioceseId(authentication, ParishAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return loadParishPageWithDeanery(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = parishScopeHelper.getRequiredActiveDeaneryId(authentication, ParishAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
            return loadParishPageWithDeanery(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_PARISH)) {
            UUID parishId = parishScopeHelper.getRequiredActiveParishId(authentication, ParishAccessEvaluator.READ_PARISH);
            scopedRequest.getFilters().put(PARISH_FILTER_FIELD, parishId);
            return loadParishPageWithDeanery(scopedRequest);
        }

        throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách giáo xứ");
    }

    @Cacheable(cacheNames = CacheNames.PARISH_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public ParishResponse findById(UUID id, Authentication authentication) {
        Parish parish = parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        return toParishResponse(parish)
                .toBuilder()
                .createdByName(auditActorNameResolver.resolve(parish.getCreatedBy()))
                .updatedByName(auditActorNameResolver.resolve(parish.getUpdatedBy()))
                .build();
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    @EvictCaches.Leaders
    @EvictCaches.Certificates
    @EvictCaches.ExecutiveAssignments
    public ParishResponse update(UUID id, ParishUpdateRequest request, Authentication authentication) {
        parishValidationHelper.validateUpdateInput(request);
        Parish existingParish = parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        toggleSupport.requireOperational(UnitType.PARISH, id);
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        parishValidationHelper.validateUpdateNameUniqueness(id, request.getName(), existingParish);

        if (request.getDeaneryId() != null) {
            Deanery targetDeanery = parishValidationHelper.requireExistingDeaneryWithDiocese(request.getDeaneryId());
            toggleSupport.requireOperational(UnitType.DEANERY, targetDeanery.getId());
            parishScopeHelper.enforceDeaneryChangeScope(authentication, existingParish, targetDeanery);
        }

        try {
            parishRepository.update(id, request, auditActor);
            return toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo xứ bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    // Đổi giáo xứ cha của giáo họ / giáo điểm (cùng giáo hạt). Huynh trưởng và tài khoản vẫn gắn vào chính giáo họ / giáo điểm.
    @EvictCaches.Analytics
    @EvictCaches.Leaders
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse changeParent(UUID id, ParishParentChangeRequest request, Authentication authentication) {
        Parish existing = parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        if (existing.getUnitType() == ParishUnitType.PARISH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Chỉ đổi giáo xứ cha cho giáo họ hoặc giáo điểm");
        }
        toggleSupport.requireOperational(UnitType.PARISH, id);
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        ParishCreateRequest probe = new ParishCreateRequest();
        probe.setParentParishId(request.getParentParishId());
        Parish parent = parishValidationHelper.resolveParent(probe, existing.getUnitType(), existing.getDeanery().getId());
        if (existing.getParentParish() == null || !existing.getParentParish().getId().equals(parent.getId())) {
            toggleSupport.requireOperational(UnitType.PARISH, parent.getId());
            parishRepository.updateParent(id, parent.getId(), auditActor);
        }
        return toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
    }

    // Nâng giáo họ / giáo điểm thành giáo xứ độc lập (không còn giáo xứ cha). Huynh trưởng, tài khoản, hồ sơ giữ nguyên.
    @EvictCaches.Analytics
    @EvictCaches.Leaders
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse promoteToParish(UUID id, Authentication authentication) {
        Parish existing = parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        if (existing.getUnitType() == ParishUnitType.PARISH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Đơn vị này đã là giáo xứ");
        }
        toggleSupport.requireOperational(UnitType.PARISH, id);
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        parishRepository.promoteToParish(id, auditActor);
        return toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
    }

    @Transactional
    @EvictCaches.Analytics
    @EvictCaches.Leaders
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        toggleSupport.requireToggleAllowed(UnitType.PARISH, id);
        Set<String> recipients = toggleSupport.recipients(UnitType.PARISH, id);
        parishValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        UnitStatusAuditService.ToggleContext toggleContext = toggleSupport.begin(UnitType.PARISH, id, request == null ? null : request.getReason());
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        parishRepository.toggleStatus(id, auditActor);
        toggleSupport.finish(toggleContext, UnitType.PARISH, id, auditActor);
        ParishResponse response = toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
        toggleSupport.announce(recipients, "Giáo xứ", response.getName(), Boolean.TRUE.equals(response.getStatus()), auditActor);
        return response;
    }

    public UnitToggleImpactResponse toggleImpact(UUID id) {
        return toggleSupport.impact(UnitType.PARISH, id);
    }

    public List<UnitStatusHistoryResponse> statusHistory(UUID id, int page, int size) {
        return toggleSupport.history(UnitType.PARISH, id, page, size);
    }






    private Page<ParishResponse> loadParishPageWithDeanery(BaseSearchRequest request) {
        Page<Parish> page = parishRepository.findAll(request);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<UUID> parishIds = page.getContent().stream()
                .map(Parish::getId)
                .toList();

        Map<UUID, Parish> parishById = parishRepository.findAllByIdsWithDeaneryAndDiocese(parishIds).stream()
                .collect(Collectors.toMap(Parish::getId, Function.identity()));
        Map<UUID, Long> leaderCounts = parishRepository.countLeadersByParishIds(parishIds);
        Map<UUID, Long> subUnitCounts = parishRepository.countSubUnitsByParentIds(parishIds);

        List<ParishResponse> hydratedContent = parishIds.stream()
                .map(parishById::get)
                .filter(java.util.Objects::nonNull)
                .map(parish -> toParishResponse(parish, leaderCounts.getOrDefault(parish.getId(), 0L), subUnitCounts.getOrDefault(parish.getId(), 0L)))
                .toList();

        return new PageImpl<>(hydratedContent, page.getPageable(), page.getTotalElements());
    }

    private ParishResponse toParishResponse(Parish parish) {
        UUID id = parish.getId();
        return toParishResponse(parish,
                id == null ? 0L : parishRepository.countLeadersByParishIds(List.of(id)).getOrDefault(id, 0L),
                id == null ? 0L : parishRepository.countSubUnitsByParentIds(List.of(id)).getOrDefault(id, 0L));
    }

    private ParishResponse toParishResponse(Parish parish, long leaderCount, long subUnitCount) {
        Parish parent = parish.getParentParish();
        return ParishResponse.builder()
                .id(parish.getId())
                .createdAt(parish.getCreatedAt())
                .createdBy(parish.getCreatedBy())
                .updatedAt(parish.getUpdatedAt())
                .updatedBy(parish.getUpdatedBy())
                .status(parish.getStatus())
                .effectiveStatus(Boolean.TRUE.equals(parish.getStatus())
                        && parish.getDeanery() != null && Boolean.TRUE.equals(parish.getDeanery().getStatus())
                        && parish.getDeanery().getDiocese() != null && Boolean.TRUE.equals(parish.getDeanery().getDiocese().getStatus()))
                .name(parish.getName())
                .patronSaintDay(parish.getPatronSaintDay())
                .establishmentDate(parish.getEstablishmentDate())
                .chaplain(parish.getChaplain())
                .phoneNumber(parish.getPhoneNumber())
                .email(parish.getEmail())
                .deaneryId(parish.getDeanery() == null ? null : parish.getDeanery().getId())
                .deaneryName(parish.getDeanery() == null ? null : parish.getDeanery().getName())
                .dioceseId(parish.getDeanery() == null || parish.getDeanery().getDiocese() == null ? null : parish.getDeanery().getDiocese().getId())
                .dioceseName(parish.getDeanery() == null || parish.getDeanery().getDiocese() == null ? null : parish.getDeanery().getDiocese().getName())
                .chapterName(parish.getChapterName())
                .unitType(parish.getUnitType())
                .unitTypeLabel(parish.getUnitType() == null ? null : parish.getUnitType().getLabel())
                .parentParishId(parent == null ? null : parent.getId())
                .parentParishName(parent == null ? null : parent.getName())
                .subUnitCount(subUnitCount)
                .leaderCount(leaderCount)
                .build();
    }
}
