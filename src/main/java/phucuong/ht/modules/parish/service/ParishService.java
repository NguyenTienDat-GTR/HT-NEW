package phucuong.ht.modules.parish.service;

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
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.parish.helper.ParishScopeHelper;
import phucuong.ht.modules.parish.helper.ParishValidationHelper;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
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
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver toggleNotificationRecipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher toggleNotificationDispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse create(ParishCreateRequest request, Authentication authentication) {
        parishValidationHelper.validateCreateInput(request);
        parishScopeHelper.enforceCreateScope(request, authentication);
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        try {
            Deanery deanery = parishValidationHelper.requireExistingDeanery(request.getDeaneryId());
            requireUnitOperational(UnitType.DEANERY, deanery.getId());
            parishValidationHelper.validateCreateNameUniqueness(request.getName(), deanery.getId());
            Parish parish = new Parish();
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
        BaseSearchRequest scopedRequest = copyRequest(request);

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
        return toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse update(UUID id, ParishUpdateRequest request, Authentication authentication) {
        parishValidationHelper.validateUpdateInput(request);
        Parish existingParish = parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        requireUnitOperational(UnitType.PARISH, id);
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        parishValidationHelper.validateUpdateNameUniqueness(id, request.getName(), existingParish);

        if (request.getDeaneryId() != null) {
            Deanery targetDeanery = parishValidationHelper.requireExistingDeaneryWithDiocese(request.getDeaneryId());
            requireUnitOperational(UnitType.DEANERY, targetDeanery.getId());
            parishScopeHelper.enforceDeaneryChangeScope(authentication, existingParish, targetDeanery);
        }

        try {
            parishRepository.update(id, request, auditActor);
            return toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo xứ bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    public ParishResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id);
        requireUnitToggleAllowed(UnitType.PARISH, id);
        Set<String> recipients = toggleNotificationRecipientResolver == null ? Set.of() : toggleNotificationRecipientResolver.byUnit(UnitType.PARISH, id);
        parishValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        String auditActor = parishValidationHelper.getRequiredAuditActor(authentication);
        parishRepository.toggleStatus(id, auditActor);
        ParishResponse response = toParishResponse(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(id));
        notifyUsers(recipients);
        dispatchUnitToggle(recipients, unitLabel("Giáo xứ", response.getName()), Boolean.TRUE.equals(response.getStatus()), auditActor);
        return response;
    }

    private void requireUnitOperational(UnitType unitType, UUID id) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireUnitOperational(unitType, id);
        }
    }

    private void requireUnitToggleAllowed(UnitType unitType, UUID id) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireUnitToggleAllowed(unitType, id);
        }
    }

    private void notifyUsers(Set<String> recipients) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyUsers(recipients, "UNIT_STATUS_TOGGLED");
        }
    }

    private void dispatchUnitToggle(Set<String> recipients, String targetLabel, boolean enabled, String actor) {
        if (toggleNotificationDispatcher != null) {
            toggleNotificationDispatcher.dispatch(recipients, "UNIT_STATUS_TOGGLED", targetLabel, enabled, actor);
        }
    }

    private String unitLabel(String unitType, String name) {
        String normalizedName = name == null ? null : name.trim();
        return normalizedName == null || normalizedName.isBlank() ? unitType : unitType + " " + normalizedName;
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

        List<ParishResponse> hydratedContent = parishIds.stream()
                .map(parishById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toParishResponse)
                .toList();

        return new PageImpl<>(hydratedContent, page.getPageable(), page.getTotalElements());
    }

    private ParishResponse toParishResponse(Parish parish) {
        return ParishResponse.builder()
                .id(parish.getId())
                .createdAt(parish.getCreatedAt())
                .createdBy(parish.getCreatedBy())
                .updatedAt(parish.getUpdatedAt())
                .updatedBy(parish.getUpdatedBy())
                .status(parish.getStatus())
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
                .build();
    }
}
