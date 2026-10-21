package phucuong.ht.modules.diocese.service;

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
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.helper.DioceseValidationHelper;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.model.response.DioceseResponse;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.diocese.security.DioceseAccessEvaluator;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.notification.service.ToggleNotificationRecipientResolver;
import phucuong.ht.modules.system.service.RbacAuthRefreshNotifier;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DioceseService {

    private static final String DIOCESE_FILTER_FIELD = "id";

    private final DioceseRepository dioceseRepository;
    private final DioceseValidationHelper dioceseValidationHelper;
    private final AccountRepository accountRepository;
    private final securityUtils securityUtils;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver toggleNotificationRecipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher toggleNotificationDispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;
    @Autowired(required = false)
    private phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public DioceseResponse create(DioceseCreateRequest request, Authentication authentication) {
        dioceseValidationHelper.validateCreateInput(request);
        String auditActor = dioceseValidationHelper.getRequiredAuditActor(authentication);
        dioceseValidationHelper.validateCreateNameUniqueness(request.getName());
        try {
            Diocese diocese = new Diocese();
            diocese.setName(request.getName());
            diocese.setPatronSaintDay(request.getPatronSaintDay());
            diocese.setEstablishmentDate(request.getEstablishmentDate());
            diocese.setChaplain(request.getChaplain());
            diocese.setPhoneNumber(request.getPhoneNumber());
            diocese.setEmail(request.getEmail());
            diocese.setUnionName(request.getUnionName());
            diocese.setStatus(Boolean.TRUE);
            diocese.setCreatedAt(OffsetDateTime.now());
            diocese.setUpdatedAt(diocese.getCreatedAt());
            diocese.setCreatedBy(auditActor);
            diocese.setUpdatedBy(auditActor);
            Diocese saved = dioceseRepository.save(diocese);
            // Mỗi giáo phận có danh mục cấp độ riêng: tạo danh mục mặc định cho giáo phận mới.
            if (leaderLevelService != null) {
                leaderLevelService.seedDefaults(saved.getId(), auditActor);
            }
            return toDioceseResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.DIOCESE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<DioceseResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);

        if (securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_ALL)) {
            return loadDiocesePage(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(securityUtils.currentUsername(authentication))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.FORBIDDEN,
                            errorCode.ACCESS_DENIED,
                            "Tài khoản hiện tại không có phạm vi giáo phận hợp lệ"
                    ));
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return loadDiocesePage(scopedRequest);
        }

        throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách giáo phận");
    }

    @Cacheable(cacheNames = CacheNames.DIOCESE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public DioceseResponse findById(UUID id, Authentication authentication) {
        return toDioceseResponse(dioceseValidationHelper.requireExistingDiocese(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public DioceseResponse update(UUID id, DioceseUpdateRequest request, Authentication authentication) {
        Diocese existingDiocese = dioceseValidationHelper.requireExistingDiocese(id);
        requireUnitOperational(UnitType.DIOCESE, id);
        dioceseValidationHelper.validateUpdateInput(request);
        String auditActor = dioceseValidationHelper.getRequiredAuditActor(authentication);
        dioceseValidationHelper.validateUpdateNameUniqueness(id, request.getName(), existingDiocese.getName());
        try {
            return toDioceseResponse(dioceseRepository.update(id, request, auditActor));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public DioceseResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        dioceseValidationHelper.requireExistingDiocese(id);
        requireUnitToggleAllowed(UnitType.DIOCESE, id);
        Set<String> recipients = toggleNotificationRecipientResolver == null ? Set.of() : toggleNotificationRecipientResolver.byUnit(UnitType.DIOCESE, id);
        dioceseValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        String auditActor = dioceseValidationHelper.getRequiredAuditActor(authentication);
        Diocese updated = dioceseRepository.toggleStatus(id, auditActor);
        notifyUsers(recipients);
        dispatchUnitToggle(recipients, unitLabel("Giáo phận", updated.getName()), Boolean.TRUE.equals(updated.getStatus()), auditActor);
        return toDioceseResponse(updated);
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

    private Page<DioceseResponse> loadDiocesePage(BaseSearchRequest request) {
        Page<Diocese> page = dioceseRepository.findAll(request);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<UUID> ids = page.getContent().stream()
                .map(Diocese::getId)
                .toList();
        Map<UUID, Long> deaneryCounts = dioceseRepository.countDeaneriesByDioceseIds(ids);
        Map<UUID, Long> parishCounts = dioceseRepository.countParishesByDioceseIds(ids);
        Map<UUID, Long> leaderCounts = dioceseRepository.countLeadersByDioceseIds(ids);

        List<DioceseResponse> content = page.getContent().stream()
                .map(diocese -> toDioceseResponse(
                        diocese,
                        deaneryCounts.getOrDefault(diocese.getId(), 0L),
                        parishCounts.getOrDefault(diocese.getId(), 0L),
                        leaderCounts.getOrDefault(diocese.getId(), 0L)
                ))
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private DioceseResponse toDioceseResponse(Diocese diocese) {
        UUID id = diocese.getId();
        return toDioceseResponse(
                diocese,
                id == null ? 0L : dioceseRepository.countDeaneriesByDioceseIds(List.of(id)).getOrDefault(id, 0L),
                id == null ? 0L : dioceseRepository.countParishesByDioceseIds(List.of(id)).getOrDefault(id, 0L),
                id == null ? 0L : dioceseRepository.countLeadersByDioceseIds(List.of(id)).getOrDefault(id, 0L)
        );
    }

    private DioceseResponse toDioceseResponse(Diocese diocese, long deaneryCount, long parishCount, long leaderCount) {
        return DioceseResponse.builder()
                .id(diocese.getId())
                .createdAt(diocese.getCreatedAt())
                .createdBy(diocese.getCreatedBy())
                .updatedAt(diocese.getUpdatedAt())
                .updatedBy(diocese.getUpdatedBy())
                .status(diocese.getStatus())
                .name(diocese.getName())
                .patronSaintDay(diocese.getPatronSaintDay())
                .establishmentDate(diocese.getEstablishmentDate())
                .chaplain(diocese.getChaplain())
                .phoneNumber(diocese.getPhoneNumber())
                .email(diocese.getEmail())
                .unionName(diocese.getUnionName())
                .deaneryCount(deaneryCount)
                .parishCount(parishCount)
                .leaderCount(leaderCount)
                .build();
    }
}
