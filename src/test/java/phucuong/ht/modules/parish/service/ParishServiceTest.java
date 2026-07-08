package phucuong.ht.modules.parish.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.helper.ParishScopeHelper;
import phucuong.ht.modules.parish.helper.ParishValidationHelper;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;
import phucuong.ht.modules.parish.model.response.ParishResponse;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.parish.security.ParishAccessEvaluator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParishServiceTest {

    @Mock
    private ParishRepository parishRepository;

    @Mock
    private ParishValidationHelper parishValidationHelper;

    @Mock
    private ParishScopeHelper parishScopeHelper;

    @Mock
    private securityUtils securityUtils;

    @InjectMocks
    private ParishService parishService;

    @Test
    void createShouldValidateScopeAndDelegateToRepository() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        ParishCreateRequest request = new ParishCreateRequest();
        request.setName("Parish");
        request.setChapterName("Chapter");
        request.setDeaneryId(deaneryId);
        Deanery deanery = deanery(deaneryId, dioceseId);
        Parish created = parish(parishId, deanery);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(parishValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(parishValidationHelper.requireExistingDeanery(deaneryId)).thenReturn(deanery);
        when(parishRepository.save(argThat(entity ->
                "Parish".equals(entity.getName())
                        && "Chapter".equals(entity.getChapterName())
                        && deaneryId.equals(entity.getDeanery().getId())
                        && "LEADER1".equals(entity.getCreatedBy())
        ))).thenAnswer(invocation -> {
            Parish entity = invocation.getArgument(0);
            entity.setId(parishId);
            return entity;
        });
        when(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(parishId)).thenReturn(created);

        ParishResponse result = parishService.create(request, authentication);

        assertEquals(deaneryId, result.getDeaneryId());
        verify(parishValidationHelper).validateCreateInput(request);
        verify(parishScopeHelper).enforceCreateScope(request, authentication);
        verify(parishValidationHelper).validateCreateNameUniqueness("Parish", deaneryId);
    }

    @Test
    void findAllShouldApplyDioceseScopeFilter() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        request.setFilters(new LinkedHashMap<>(java.util.Map.of("name", "ignored")));
        Parish summary = new Parish();
        summary.setId(parishId);
        Parish hydrated = parish(parishId, deanery(deaneryId, dioceseId));
        Page<Parish> page = new PageImpl<>(List.of(summary));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_DIOCESE)).thenReturn(true);
        when(parishScopeHelper.getRequiredActiveDioceseId(authentication, ParishAccessEvaluator.READ_DIOCESE)).thenReturn(dioceseId);
        when(parishRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(parishRepository.findAllByIdsWithDeaneryAndDiocese(List.of(parishId))).thenReturn(List.of(hydrated));

        Page<ParishResponse> result = parishService.findAll(request, authentication);

        assertEquals(deaneryId, result.getContent().getFirst().getDeaneryId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(parishRepository).findAll(captor.capture());
        assertEquals(dioceseId, captor.getValue().getFilters().get("deanery.diocese.id"));
        assertEquals("ignored", captor.getValue().getFilters().get("name"));
    }

    @Test
    void findAllShouldApplyParishScopeFilter() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        Parish summary = new Parish();
        summary.setId(parishId);
        Parish hydrated = parish(parishId, deanery(deaneryId, dioceseId));
        Page<Parish> page = new PageImpl<>(List.of(summary));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_DIOCESE)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_DEANERY)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.READ_PARISH)).thenReturn(true);
        when(parishScopeHelper.getRequiredActiveParishId(authentication, ParishAccessEvaluator.READ_PARISH)).thenReturn(parishId);
        when(parishRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(parishRepository.findAllByIdsWithDeaneryAndDiocese(List.of(parishId))).thenReturn(List.of(hydrated));

        Page<ParishResponse> result = parishService.findAll(request, authentication);

        assertEquals(parishId, result.getContent().getFirst().getId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(parishRepository).findAll(captor.capture());
        assertEquals(parishId, captor.getValue().getFilters().get("id"));
    }

    @Test
    void updateShouldAllowDeaneryChangeWithinSameDiocese() {
        UUID parishId = UUID.randomUUID();
        UUID currentDeaneryId = UUID.randomUUID();
        UUID targetDeaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        ParishUpdateRequest request = new ParishUpdateRequest();
        request.setName("Updated");
        request.setDeaneryId(targetDeaneryId);
        Parish existing = parish(parishId, deanery(currentDeaneryId, dioceseId));
        existing.setName("Old");
        Deanery targetDeanery = deanery(targetDeaneryId, dioceseId);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(parishId)).thenReturn(existing, existing);
        when(parishValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(parishValidationHelper.requireExistingDeaneryWithDiocese(targetDeaneryId)).thenReturn(targetDeanery);
        when(parishRepository.update(parishId, request, "LEADER1")).thenAnswer(invocation -> {
            existing.setName("Updated");
            existing.setDeanery(targetDeanery);
            return existing;
        });

        ParishResponse result = parishService.update(parishId, request, authentication);

        assertEquals(targetDeaneryId, result.getDeaneryId());
        verify(parishScopeHelper).enforceDeaneryChangeScope(authentication, existing, targetDeanery);
    }

    @Test
    void updateShouldPropagateParishAdminDeaneryChangeRejection() {
        UUID parishId = UUID.randomUUID();
        UUID currentDeaneryId = UUID.randomUUID();
        UUID targetDeaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        ParishUpdateRequest request = new ParishUpdateRequest();
        request.setDeaneryId(targetDeaneryId);
        Parish existing = parish(parishId, deanery(currentDeaneryId, dioceseId));
        Deanery targetDeanery = deanery(targetDeaneryId, dioceseId);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(parishValidationHelper.requireExistingParishWithDeaneryAndDiocese(parishId)).thenReturn(existing);
        when(parishValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(parishValidationHelper.requireExistingDeaneryWithDiocese(targetDeaneryId)).thenReturn(targetDeanery);
        doThrow(new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Bị cấm"))
                .when(parishScopeHelper)
                .enforceDeaneryChangeScope(authentication, existing, targetDeanery);

        ApiException exception = assertThrows(ApiException.class, () -> parishService.update(parishId, request, authentication));

        assertEquals("Bị cấm", exception.getMessage());
    }

    @Test
    void deleteShouldWrapDataConflict() {
        UUID parishId = UUID.randomUUID();
        Parish parish = new Parish();
        parish.setId(parishId);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(parishValidationHelper.requireExistingParish(parishId)).thenReturn(parish);
        doThrow(new DataIntegrityViolationException("fk")).when(parishRepository).delete(parishId);

        ApiException exception = assertThrows(ApiException.class, () -> parishService.delete(parishId, authentication));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(errorCode.CONFLICT, exception.getErrorCode());
    }

    private Parish parish(UUID parishId, Deanery deanery) {
        Parish parish = new Parish();
        parish.setId(parishId);
        parish.setName("Sample Parish");
        parish.setChapterName("Sample Chapter");
        parish.setDeanery(deanery);
        return parish;
    }

    private Deanery deanery(UUID deaneryId, UUID dioceseId) {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        deanery.setDiocese(diocese);
        return deanery;
    }
}
