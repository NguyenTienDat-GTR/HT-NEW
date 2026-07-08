package phucuong.ht.modules.deanery.service;

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
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.helper.DeaneryScopeHelper;
import phucuong.ht.modules.deanery.helper.DeaneryValidationHelper;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeaneryService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.id";
    private static final String DEANERY_FILTER_FIELD = "id";

    private final DeaneryRepository deaneryRepository;
    private final DeaneryValidationHelper deaneryValidationHelper;
    private final DeaneryScopeHelper deaneryScopeHelper;
    private final securityUtils securityUtils;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true)
    })
    public DeaneryResponse create(DeaneryCreateRequest request, Authentication authentication) {
        deaneryValidationHelper.validateCreateInput(request);
        deaneryScopeHelper.enforceCreateScope(request, authentication);
        String auditActor = deaneryValidationHelper.getRequiredAuditActor(authentication);
        try {
            Diocese diocese = deaneryValidationHelper.requireExistingDiocese(request.getDioceseId());
            deaneryValidationHelper.validateCreateNameUniqueness(request.getName(), diocese.getId());
            Deanery deanery = new Deanery();
            deanery.setDiocese(diocese);
            deanery.setName(request.getName());
            deanery.setPatronSaintDay(request.getPatronSaintDay());
            deanery.setEstablishmentDate(request.getEstablishmentDate());
            deanery.setChaplain(request.getChaplain());
            deanery.setPhoneNumber(request.getPhoneNumber());
            deanery.setEmail(request.getEmail());
            deanery.setAssociationName(request.getAssociationName());
            deanery.setStatus(Boolean.TRUE);
            deanery.setCreatedAt(OffsetDateTime.now());
            deanery.setUpdatedAt(deanery.getCreatedAt());
            deanery.setCreatedBy(auditActor);
            deanery.setUpdatedBy(auditActor);
            deaneryRepository.save(deanery);
            return toDeaneryResponse(deaneryValidationHelper.requireExistingDeaneryWithDiocese(deanery.getId()));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo hạt bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.DEANERY_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<DeaneryResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);

        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_ALL)) {
            return loadDeaneryPageWithDiocese(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = deaneryScopeHelper.getRequiredActiveDioceseId(authentication, DeaneryAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return loadDeaneryPageWithDiocese(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = deaneryScopeHelper.getRequiredActiveDeaneryId(authentication, DeaneryAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
            return loadDeaneryPageWithDiocese(scopedRequest);
        }

        throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách giáo hạt");
    }

    @Cacheable(cacheNames = CacheNames.DEANERY_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public DeaneryResponse findById(UUID id, Authentication authentication) {
        return toDeaneryResponse(deaneryValidationHelper.requireExistingDeaneryWithDiocese(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true)
    })
    public DeaneryResponse update(UUID id, DeaneryUpdateRequest request, Authentication authentication) {
        deaneryValidationHelper.validateUpdateInput(request);
        Deanery existingDeanery = deaneryValidationHelper.requireExistingDeaneryWithDiocese(id);
        String auditActor = deaneryValidationHelper.getRequiredAuditActor(authentication);
        deaneryValidationHelper.validateUpdateNameUniqueness(id, request.getName(), existingDeanery);
        try {
            deaneryRepository.update(id, request, auditActor);
            return toDeaneryResponse(deaneryValidationHelper.requireExistingDeaneryWithDiocese(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo hạt bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true)
    })
    public void delete(UUID id, Authentication authentication) {
        Deanery deanery = deaneryValidationHelper.requireExistingDeanery(id);
        deaneryScopeHelper.enforceDeleteScope(authentication, deanery);
        try {
            deaneryRepository.delete(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Không thể xóa giáo hạt vì đang được dữ liệu khác tham chiếu");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true)
    })
    public DeaneryResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        deaneryValidationHelper.requireExistingDeaneryWithDiocese(id);
        deaneryValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        String auditActor = deaneryValidationHelper.getRequiredAuditActor(authentication);
        deaneryRepository.toggleStatus(id, auditActor);
        return toDeaneryResponse(deaneryValidationHelper.requireExistingDeaneryWithDiocese(id));
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

    private Page<DeaneryResponse> loadDeaneryPageWithDiocese(BaseSearchRequest request) {
        Page<Deanery> page = deaneryRepository.findAll(request);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<UUID> deaneryIds = page.getContent().stream()
                .map(Deanery::getId)
                .toList();

        Map<UUID, Deanery> deaneryById = deaneryRepository.findAllByIdsWithDiocese(deaneryIds).stream()
                .collect(Collectors.toMap(Deanery::getId, Function.identity()));

        List<DeaneryResponse> hydratedContent = deaneryIds.stream()
                .map(deaneryById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toDeaneryResponse)
                .toList();

        return new PageImpl<>(hydratedContent, page.getPageable(), page.getTotalElements());
    }

    private DeaneryResponse toDeaneryResponse(Deanery deanery) {
        return DeaneryResponse.builder()
                .id(deanery.getId())
                .createdAt(deanery.getCreatedAt())
                .createdBy(deanery.getCreatedBy())
                .updatedAt(deanery.getUpdatedAt())
                .updatedBy(deanery.getUpdatedBy())
                .status(deanery.getStatus())
                .name(deanery.getName())
                .patronSaintDay(deanery.getPatronSaintDay())
                .establishmentDate(deanery.getEstablishmentDate())
                .chaplain(deanery.getChaplain())
                .phoneNumber(deanery.getPhoneNumber())
                .email(deanery.getEmail())
                .associationName(deanery.getAssociationName())
                .dioceseId(deanery.getDiocese() == null ? null : deanery.getDiocese().getId())
                .build();
    }
}
