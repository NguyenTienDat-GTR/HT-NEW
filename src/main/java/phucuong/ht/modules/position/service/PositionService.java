package phucuong.ht.modules.position.service;

import lombok.RequiredArgsConstructor;
import phucuong.ht.common.repository.InMemoryPager;
import phucuong.ht.common.repository.SearchRequests;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.model.request.PositionCreateRequest;
import phucuong.ht.modules.position.model.request.PositionUpdateRequest;
import phucuong.ht.modules.position.model.response.PositionResponse;
import phucuong.ht.modules.position.repository.PositionRepository;
import phucuong.ht.modules.position.security.PositionOwnership;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;
    private final AuditActorNameResolver auditActorNameResolver;
    private final AccountRepository accountRepository;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.POSITION_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POSITION_DETAIL, allEntries = true)
    })
    @Transactional
    public PositionResponse create(PositionCreateRequest request, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo chức vụ");
        UUID dioceseId = requireCurrentDioceseId(authentication, "Tài khoản không thuộc giáo phận nào nên không tạo được chức vụ");
        PositionOwnership ownership = ownership();
        UUID unitId = ownership.ownerUnitForCreate(ownership.viewer(securityUtils.currentUsername(authentication), authentication), request.getPositionType());
        if (unitId == null) {
            throw commonValidationHelper.forbidden("Tài khoản không thuộc đơn vị cấp này nên không tạo được chức vụ");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Position position = new Position();
        position.setDioceseId(dioceseId);
        position.setUnitId(unitId);
        position.setPositionName(commonValidationHelper.normalizeRequiredText(request.getPositionName()));
        position.setPositionType(request.getPositionType());
        position.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : positionRepository.nextDisplayOrder(request.getPositionType(), unitId));
        position.setStatus(Boolean.TRUE);
        position.setCreatedAt(now);
        position.setUpdatedAt(now);
        position.setCreatedBy(actor);
        position.setUpdatedBy(actor);
        positionRepository.save(position);
        return toDetailResponse(position);
    }

    @Cacheable(cacheNames = CacheNames.POSITION_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<PositionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        PositionOwnership ownership = ownership();
        PositionOwnership.Viewer viewer = ownership.viewer(securityUtils.currentUsername(authentication), authentication);
        List<Position> readable = loadAllMatching(request).stream()
                .filter(position -> canReadType(authentication, position.getPositionType()))
                .filter(position -> ownership.canAccess(viewer, position))
                .toList();
        return page(request, readable);
    }

    @Cacheable(cacheNames = CacheNames.POSITION_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #positionCode)")
    public PositionResponse findById(String positionCode, Authentication authentication) {
        Position position = positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode));
        if (!canReadType(authentication, position.getPositionType()) || !canAccess(position, authentication)) {
            throw commonValidationHelper.forbidden("Bạn không có quyền xem chức vụ này");
        }
        return toDetailResponse(position);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.POSITION_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POSITION_DETAIL, allEntries = true)
    })
    @Transactional
    @EvictCaches.ExecutiveAssignments
    public PositionResponse update(String positionCode, PositionUpdateRequest request, Authentication authentication) {
        Position position = positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode));
        requireManage(position, authentication);
        if (request.getPositionType() != null && request.getPositionType() != position.getPositionType()) {
            throw commonValidationHelper.validationError("Không đổi được cấp của chức vụ đã tạo");
        }
        if (request.getPositionName() != null) {
            position.setPositionName(commonValidationHelper.normalizeRequiredText(request.getPositionName()));
        }
        if (request.getDisplayOrder() != null) {
            position.setDisplayOrder(request.getDisplayOrder());
        }
        position.setUpdatedAt(OffsetDateTime.now());
        position.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật chức vụ"));
        return toDetailResponse(position);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.POSITION_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POSITION_DETAIL, allEntries = true)
    })
    @EvictCaches.ExecutiveAssignments
    public PositionResponse toggleStatus(String positionCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái chức vụ");
        Position existing = positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode));
        requireManage(existing, authentication);
        return toDetailResponse(positionRepository.toggleStatus(positionCode, actor));
    }

    private List<Position> loadAllMatching(BaseSearchRequest request) {
        List<Position> matches = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = SearchRequests.copy(request);
            batch.setPage(page);
            batch.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
            Page<Position> result = positionRepository.findAll(batch);
            matches.addAll(result.getContent());
            if (!result.hasNext()) {
                return matches;
            }
            page++;
        }
    }

    private Page<PositionResponse> page(BaseSearchRequest request, List<Position> positions) {
        return InMemoryPager.page(request, positions, this::toResponse);
    }

    /** Giáo phận của tài khoản hiện tại; null nếu tài khoản không thuộc giáo phận nào (vd. super-admin). */
    private UUID currentDioceseId(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return null;
        }
        return accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
    }

    private UUID requireCurrentDioceseId(Authentication authentication, String message) {
        UUID dioceseId = currentDioceseId(authentication);
        if (dioceseId == null) {
            throw commonValidationHelper.forbidden(message);
        }
        return dioceseId;
    }

    private PositionOwnership ownership() {
        return new PositionOwnership(accountRepository, positionRepository);
    }

    private boolean canAccess(Position position, Authentication authentication) {
        PositionOwnership ownership = ownership();
        return ownership.canAccess(ownership.viewer(securityUtils.currentUsername(authentication), authentication), position);
    }

    /** Cấp trên chỉ xem được chức vụ của đơn vị bên dưới, không sửa hay đổi trạng thái. */
    private void requireManage(Position position, Authentication authentication) {
        PositionOwnership ownership = ownership();
        if (!ownership.canManage(ownership.viewer(securityUtils.currentUsername(authentication), authentication), position)) {
            throw commonValidationHelper.forbidden("Chỉ đơn vị sở hữu mới được sửa chức vụ này");
        }
    }

    private boolean canReadType(Authentication authentication, UnitType positionType) {
        if (positionType == null) {
            return false;
        }
        return securityUtils.hasAuthority(authentication,
                "organization.position.read." + positionType.name().toLowerCase(java.util.Locale.ROOT));
    }

    private PositionResponse toResponse(Position position) {
        return responseBuilder(position).build();
    }

    private PositionResponse toDetailResponse(Position position) {
        return responseBuilder(position)
                .createdByName(auditActorNameResolver.resolve(position.getCreatedBy()))
                .updatedByName(auditActorNameResolver.resolve(position.getUpdatedBy()))
                .build();
    }

    private PositionResponse.PositionResponseBuilder responseBuilder(Position position) {
        return PositionResponse.builder()
                .positionCode(position.getPositionCode())
                .dioceseId(position.getDioceseId())
                .unitId(position.getUnitId())
                .positionName(position.getPositionName())
                .positionType(position.getPositionType())
                .displayOrder(position.getDisplayOrder())
                .status(position.getStatus())
                .createdAt(position.getCreatedAt())
                .createdBy(position.getCreatedBy())
                .updatedAt(position.getUpdatedAt())
                .updatedBy(position.getUpdatedBy());
    }
}
