package phucuong.ht.modules.diocese.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.helper.DioceseValidationHelper;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.diocese.security.DioceseAccessEvaluator;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DioceseService {

    private static final String DIOCESE_FILTER_FIELD = "id";

    private final DioceseRepository dioceseRepository;
    private final DioceseValidationHelper dioceseValidationHelper;
    private final AccountRepository accountRepository;
    private final securityUtils securityUtils;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public Diocese create(DioceseCreateRequest request, Authentication authentication) {
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
            return dioceseRepository.save(diocese);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.DIOCESE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<Diocese> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);

        if (securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_ALL)) {
            return dioceseRepository.findAll(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(securityUtils.currentUsername(authentication))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.FORBIDDEN,
                            errorCode.ACCESS_DENIED,
                            "Tài khoản hiện tại không có phạm vi giáo phận hợp lệ"
                    ));
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return dioceseRepository.findAll(scopedRequest);
        }

        throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách giáo phận");
    }

    @Cacheable(cacheNames = CacheNames.DIOCESE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public Diocese findById(UUID id, Authentication authentication) {
        return dioceseValidationHelper.requireExistingDiocese(id);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public Diocese update(UUID id, DioceseUpdateRequest request, Authentication authentication) {
        Diocese existingDiocese = dioceseValidationHelper.requireExistingDiocese(id);
        dioceseValidationHelper.validateUpdateInput(request);
        String auditActor = dioceseValidationHelper.getRequiredAuditActor(authentication);
        dioceseValidationHelper.validateUpdateNameUniqueness(id, request.getName(), existingDiocese.getName());
        try {
            return dioceseRepository.update(id, request, auditActor);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public void delete(UUID id) {
        dioceseValidationHelper.requireExistingDiocese(id);
        try {
            dioceseRepository.delete(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Không thể xóa giáo phận vì đang được dữ liệu khác tham chiếu");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true)
    })
    public Diocese toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        dioceseValidationHelper.requireExistingDiocese(id);
        dioceseValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        String auditActor = dioceseValidationHelper.getRequiredAuditActor(authentication);
        return dioceseRepository.toggleStatus(id, auditActor);
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
}
