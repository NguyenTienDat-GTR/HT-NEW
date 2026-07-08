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
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.diocese.helper.DioceseValidationHelper;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DioceseService {

    private final DioceseRepository dioceseRepository;
    private final DioceseValidationHelper dioceseValidationHelper;

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
        return dioceseRepository.findAll(request == null ? new BaseSearchRequest() : request);
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
}
