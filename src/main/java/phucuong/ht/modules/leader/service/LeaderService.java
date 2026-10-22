package phucuong.ht.modules.leader.service;

import phucuong.ht.config.EvictCaches;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.helper.LeaderScopeHelper;
import phucuong.ht.modules.leader.helper.LeaderValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;
import phucuong.ht.modules.rankHistory.model.response.RankHistoryResponse;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderService {

    private static final String DIOCESE_FILTER_FIELD = "parish.deanery.diocese.id";
    private static final String DEANERY_FILTER_FIELD = "parish.deanery.id";
    private static final String PARISH_FILTER_FIELD = "parish.id";

    private final phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;
    private final LeaderRepository leaderRepository;
    private final LeaderValidationHelper leaderValidationHelper;
    private final LeaderScopeHelper leaderScopeHelper;
    private final AccountRepository accountRepository;
    private final RankHistoryRepository rankHistoryRepository;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.security.UnitOperationalStatusService unitOperationalStatusService;

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse create(LeaderCreateRequest request, Authentication authentication) {
        leaderValidationHelper.validateCreateInput(request);
        leaderScopeHelper.enforceCreateScope(request, authentication);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);

        try {
            Parish parish = leaderValidationHelper.requireExistingParish(request.getParishId());
            requireParishOperational(parish.getId());
            leaderValidationHelper.validateCreateEmailUniqueness(request.getEmail());
            LeaderValidationHelper.LeaderNameParts nameParts = leaderValidationHelper.splitFullName(request.getFullName());
            Leader leader = new Leader();
            leader.setHolyName(request.getHolyName());
            leader.setFirstName(nameParts.firstName());
            leader.setLastName(nameParts.lastName());
            leader.setBirthDate(request.getBirthDate());
            leader.setEmail(request.getEmail());
            leader.setPhoneNumber(request.getPhoneNumber());
            leader.setImageUrl(request.getImageUrl());
            leader.setGender(request.getGender());
            leader.setLeaderLevel(java.util.Objects.requireNonNullElse(leaderLevelService.requireExistingOrNull(request.getLeaderLevel(), "Cấp độ",
                    parish.getDeanery() == null || parish.getDeanery().getDiocese() == null ? null : parish.getDeanery().getDiocese().getId()), "NONE"));
            leader.setParish(parish);
            leader.setStatus(Boolean.TRUE);
            OffsetDateTime now = OffsetDateTime.now();
            leader.setCreatedAt(now);
            leader.setUpdatedAt(now);
            leader.setCreatedBy(auditActor);
            leader.setUpdatedBy(auditActor);
            leaderRepository.save(leader);
            return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(leader.getId()));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu Huynh Trưởng bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.LEADER_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<LeaderResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        if (!hasReadAuthority(authentication)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách Huynh Trưởng");
        }

        BaseSearchRequest scopedRequest = copyRequest(request);
        if (hasAuthority(authentication, LeaderAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = leaderScopeHelper.getRequiredActiveDioceseId(authentication, LeaderAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return loadLeaderPageWithParish(scopedRequest);
        }

        if (hasAuthority(authentication, LeaderAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = leaderScopeHelper.getRequiredActiveDeaneryId(authentication, LeaderAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
            return loadLeaderPageWithParish(scopedRequest);
        }

        UUID parishId = leaderScopeHelper.getRequiredActiveParishId(authentication, LeaderAccessEvaluator.READ_PARISH);
        scopedRequest.getFilters().put(PARISH_FILTER_FIELD, parishId);
        return loadLeaderPageWithParish(scopedRequest);
    }

    public List<LeaderResponse> findAccountCandidates(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        int targetSize = Math.max(source.getSize(), 100);
        List<LeaderResponse> candidates = new ArrayList<>();
        int pageIndex = Math.max(source.getPage(), 0);
        Page<LeaderResponse> page;
        do {
            BaseSearchRequest candidateRequest = copyRequest(source);
            candidateRequest.setPage(pageIndex++);
            candidateRequest.setSize(targetSize);
            page = findAll(candidateRequest, authentication);
            page.getContent().stream()
                    .filter(leader -> leader.getId() != null && !accountRepository.existsByLeaderId(leader.getId()))
                    .forEach(candidates::add);
        } while (candidates.size() < targetSize && !page.isLast());
        return candidates.stream().limit(targetSize).toList();
    }

    @Cacheable(cacheNames = CacheNames.LEADER_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public LeaderResponse findById(UUID id, Authentication authentication) {
        return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
    }

    public List<RankHistoryResponse> findRankHistory(UUID id) {
        return rankHistoryRepository.findAllByLeaderId(id).stream()
                .map(this::toRankHistoryResponse)
                .toList();
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse update(UUID id, LeaderUpdateRequest request, Authentication authentication) {
        leaderValidationHelper.validateUpdateInput(request);
        Leader existingLeader = leaderValidationHelper.requireExistingLeaderWithParish(id);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);
        leaderValidationHelper.validateUpdateEmailUniqueness(id, request.getEmail(), existingLeader.getEmail());

        UUID currentParishId = existingLeader.getParish() == null ? null : existingLeader.getParish().getId();
        boolean transferringParish = request.getParishId() != null && !request.getParishId().equals(currentParishId);
        if (request.getParishId() != null) {
            leaderValidationHelper.requireExistingParish(request.getParishId());
            leaderScopeHelper.enforceUpdateParishScope(authentication, existingLeader, request.getParishId());
        }
        // Đóng băng: không sửa hồ sơ trong đơn vị bị khóa; riêng chuyển sang giáo xứ đang hoạt động thì được phép.
        requireParishOperational(transferringParish ? request.getParishId() : currentParishId);

        try {
            leaderRepository.update(id, request, auditActor);
            return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu Huynh Trưởng bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        leaderValidationHelper.requireExistingLeaderWithParish(id);
        leaderValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);
        leaderRepository.toggleStatus(id, auditActor);
        return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    private void requireParishOperational(UUID parishId) {
        if (unitOperationalStatusService != null && parishId != null) {
            unitOperationalStatusService.requireUnitOperational(phucuong.ht.common.enums.UnitType.PARISH, parishId);
        }
    }

    private boolean hasReadAuthority(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(authority -> LeaderAccessEvaluator.READ_DIOCESE.equals(authority)
                        || LeaderAccessEvaluator.READ_DEANERY.equals(authority)
                        || LeaderAccessEvaluator.READ_PARISH.equals(authority));
    }

    private boolean hasAuthority(Authentication authentication, String permissionCode) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(permissionCode::equals);
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

    private Page<LeaderResponse> loadLeaderPageWithParish(BaseSearchRequest request) {
        Page<Leader> page = leaderRepository.findAll(request);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<UUID> ids = page.getContent().stream()
                .map(Leader::getId)
                .toList();

        Map<UUID, Leader> leaderById = leaderRepository.findAllByIdsWithParish(ids).stream()
                .collect(Collectors.toMap(Leader::getId, Function.identity()));

        List<LeaderResponse> hydratedContent = ids.stream()
                .map(leaderById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toLeaderResponse)
                .toList();

        return new PageImpl<>(hydratedContent, page.getPageable(), page.getTotalElements());
    }

    private LeaderResponse toLeaderResponse(Leader leader) {
        return LeaderResponse.builder()
                .id(leader.getId())
                .createdAt(leader.getCreatedAt())
                .createdBy(leader.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(leader.getCreatedBy()))
                .updatedAt(leader.getUpdatedAt())
                .updatedBy(leader.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(leader.getUpdatedBy()))
                .status(leader.getStatus())
                .holyName(leader.getHolyName())
                .fullName(buildFullName(leader))
                .firstName(leader.getFirstName())
                .lastName(leader.getLastName())
                .birthDate(leader.getBirthDate())
                .email(leader.getEmail())
                .phoneNumber(leader.getPhoneNumber())
                .imageUrl(leader.getImageUrl())
                .gender(leader.getGender())
                .leaderLevel(leader.getLeaderLevel())
                .deaneryId(leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getId())
                .deaneryName(leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getName())
                .parishId(leader.getParish() == null ? null : leader.getParish().getId())
                .parishName(leader.getParish() == null ? null : leader.getParish().getName())
                .build();
    }

    private RankHistoryResponse toRankHistoryResponse(RankHistory rankHistory) {
        return RankHistoryResponse.builder()
                .id(rankHistory.getId())
                .oldLevel(rankHistory.getOldLevel())
                .newLevel(rankHistory.getNewLevel())
                .promotionDate(rankHistory.getPromotionDate())
                .note(rankHistory.getNote())
                .createdAt(rankHistory.getCreatedAt())
                .createdBy(rankHistory.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(rankHistory.getCreatedBy()))
                .updatedAt(rankHistory.getUpdatedAt())
                .updatedBy(rankHistory.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(rankHistory.getUpdatedBy()))
                .build();
    }

    private String buildFullName(Leader leader) {
        if (leader == null) {
            return null;
        }
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }
}
