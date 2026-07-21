package phucuong.ht.modules.position.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.model.request.PositionCreateRequest;
import phucuong.ht.modules.position.model.request.PositionUpdateRequest;
import phucuong.ht.modules.position.model.response.PositionResponse;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.POSITION_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POSITION_DETAIL, allEntries = true)
    })
    public PositionResponse create(PositionCreateRequest request, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo chức vụ");
        Position position = new Position();
        position.setPositionName(commonValidationHelper.normalizeRequiredText(request.getPositionName()));
        position.setPositionType(request.getPositionType());
        position.setStatus(Boolean.TRUE);
        position.setCreatedBy(actor);
        position.setUpdatedBy(actor);
        positionRepository.save(position);
        return toResponse(position);
    }

    @Cacheable(cacheNames = CacheNames.POSITION_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<PositionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<Position> readable = loadAllMatching(request).stream()
                .filter(position -> canReadType(authentication, position.getPositionType()))
                .toList();
        return page(request, readable);
    }

    @Cacheable(cacheNames = CacheNames.POSITION_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #positionCode)")
    public PositionResponse findById(String positionCode, Authentication authentication) {
        return toResponse(positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.POSITION_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POSITION_DETAIL, allEntries = true)
    })
    public PositionResponse update(String positionCode, PositionUpdateRequest request, Authentication authentication) {
        Position position = positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode));
        if (request.getPositionName() != null) {
            position.setPositionName(commonValidationHelper.normalizeRequiredText(request.getPositionName()));
        }
        if (request.getPositionType() != null) {
            position.setPositionType(request.getPositionType());
        }
        position.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật chức vụ"));
        return toResponse(position);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.POSITION_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POSITION_DETAIL, allEntries = true)
    })
    public PositionResponse toggleStatus(String positionCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái chức vụ");
        positionRepository.toggleStatus(positionCode, actor);
        return findById(positionCode, authentication);
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

    private List<Position> loadAllMatching(BaseSearchRequest request) {
        List<Position> matches = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = copyRequest(request);
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
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, positions.size());
        int end = Math.min(start + size, positions.size());
        return new PageImpl<>(positions.subList(start, end).stream().map(this::toResponse).toList(),
                org.springframework.data.domain.PageRequest.of(page, size), positions.size());
    }

    private boolean canReadType(Authentication authentication, UnitType positionType) {
        if (positionType == null) {
            return false;
        }
        return securityUtils.hasAuthority(authentication,
                "organization.position.read." + positionType.name().toLowerCase(java.util.Locale.ROOT));
    }

    private PositionResponse toResponse(Position position) {
        return PositionResponse.builder()
                .positionCode(position.getPositionCode())
                .positionName(position.getPositionName())
                .positionType(position.getPositionType())
                .status(position.getStatus())
                .createdAt(position.getCreatedAt())
                .updatedAt(position.getUpdatedAt())
                .build();
    }
}
