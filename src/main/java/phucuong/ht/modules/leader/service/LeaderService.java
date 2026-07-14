package phucuong.ht.modules.leader.service;

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
import phucuong.ht.modules.leader.helper.LeaderScopeHelper;
import phucuong.ht.modules.leader.helper.LeaderValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.parish.model.entity.Parish;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderService {

    private static final String DIOCESE_FILTER_FIELD = "parish.deanery.diocese.id";
    private static final String DEANERY_FILTER_FIELD = "parish.deanery.id";
    private static final String PARISH_FILTER_FIELD = "parish.id";

    private final LeaderRepository leaderRepository;
    private final LeaderValidationHelper leaderValidationHelper;
    private final LeaderScopeHelper leaderScopeHelper;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse create(LeaderCreateRequest request, Authentication authentication) {
        leaderValidationHelper.validateCreateInput(request);
        leaderScopeHelper.enforceCreateScope(request, authentication);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);

        try {
            Parish parish = leaderValidationHelper.requireExistingParish(request.getParishId());
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
            leader.setLeaderLevel(request.getLeaderLevel());
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
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu leader bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.LEADER_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<LeaderResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        if (!hasReadAuthority(authentication)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách leader");
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

    @Cacheable(cacheNames = CacheNames.LEADER_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public LeaderResponse findById(UUID id, Authentication authentication) {
        return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse update(UUID id, LeaderUpdateRequest request, Authentication authentication) {
        leaderValidationHelper.validateUpdateInput(request);
        Leader existingLeader = leaderValidationHelper.requireExistingLeaderWithParish(id);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);
        leaderValidationHelper.validateUpdateEmailUniqueness(id, request.getEmail(), existingLeader.getEmail());

        if (request.getParishId() != null) {
            leaderValidationHelper.requireExistingParish(request.getParishId());
            leaderScopeHelper.enforceUpdateParishScope(authentication, existingLeader, request.getParishId());
        }

        try {
            leaderRepository.update(id, request, auditActor);
            return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu leader bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Caching(evict = {
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
    public void delete(UUID id) {
        leaderValidationHelper.requireExistingLeaderWithParish(id);
        leaderRepository.delete(id);
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
                .updatedAt(leader.getUpdatedAt())
                .updatedBy(leader.getUpdatedBy())
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
                .parishId(leader.getParish() == null ? null : leader.getParish().getId())
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
