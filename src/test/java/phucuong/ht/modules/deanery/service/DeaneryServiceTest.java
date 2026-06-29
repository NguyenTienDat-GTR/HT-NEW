package phucuong.ht.modules.deanery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeaneryServiceTest {

    @Mock
    private DeaneryRepository deaneryRepository;

    @Mock
    private DeaneryValidationHelper deaneryValidationHelper;

    @Mock
    private DeaneryScopeHelper deaneryScopeHelper;

    @Mock
    private securityUtils securityUtils;

    @InjectMocks
    private DeaneryService deaneryService;

    @Test
    void createShouldValidateScopeAndDelegateToRepository() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setName("Deanery");
        request.setAssociationName("Association");
        request.setDioceseId(dioceseId);
        Diocese diocese = diocese(dioceseId);
        Deanery created = deanery(deaneryId, diocese);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(deaneryValidationHelper.requireExistingDiocese(dioceseId)).thenReturn(diocese);
        when(deaneryRepository.save(argThat(entity ->
                "Deanery".equals(entity.getName())
                        && "Association".equals(entity.getAssociationName())
                        && dioceseId.equals(entity.getDiocese().getId())
                        && "LEADER1".equals(entity.getCreatedBy())
        ))).thenAnswer(invocation -> {
            Deanery entity = invocation.getArgument(0);
            entity.setId(deaneryId);
            return entity;
        });
        when(deaneryValidationHelper.requireExistingDeaneryWithDiocese(deaneryId)).thenReturn(created);

        DeaneryResponse result = deaneryService.create(request, authentication);

        assertEquals(dioceseId, result.getDioceseId());
        verify(deaneryValidationHelper).validateCreateInput(request);
        verify(deaneryScopeHelper).enforceCreateScope(request, authentication);
        verify(deaneryValidationHelper).validateCreateNameUniqueness("Deanery", dioceseId);
    }

    @Test
    void findAllShouldApplyDioceseScopeFilter() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        request.setFilters(new LinkedHashMap<>(java.util.Map.of("name", "ignored")));
        Deanery summary = new Deanery();
        summary.setId(deaneryId);
        Deanery hydrated = deanery(deaneryId, diocese(dioceseId));
        Page<Deanery> page = new PageImpl<>(List.of(summary));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DIOCESE)).thenReturn(true);
        when(deaneryScopeHelper.getRequiredActiveDioceseId(authentication, DeaneryAccessEvaluator.READ_DIOCESE)).thenReturn(dioceseId);
        when(deaneryRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(deaneryRepository.findAllByIdsWithDiocese(List.of(deaneryId))).thenReturn(List.of(hydrated));

        Page<DeaneryResponse> result = deaneryService.findAll(request, authentication);

        assertEquals(dioceseId, result.getContent().getFirst().getDioceseId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(deaneryRepository).findAll(captor.capture());
        assertEquals(dioceseId, captor.getValue().getFilters().get("diocese.id"));
        assertEquals("ignored", captor.getValue().getFilters().get("name"));
    }

    @Test
    void findAllShouldApplyDeaneryScopeFilter() {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        Deanery summary = new Deanery();
        summary.setId(deaneryId);
        Deanery hydrated = deanery(deaneryId, diocese(dioceseId));
        Page<Deanery> page = new PageImpl<>(List.of(summary));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DIOCESE)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DEANERY)).thenReturn(true);
        when(deaneryScopeHelper.getRequiredActiveDeaneryId(authentication, DeaneryAccessEvaluator.READ_DEANERY)).thenReturn(deaneryId);
        when(deaneryRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(deaneryRepository.findAllByIdsWithDiocese(List.of(deaneryId))).thenReturn(List.of(hydrated));

        Page<DeaneryResponse> result = deaneryService.findAll(request, authentication);

        assertEquals(deaneryId, result.getContent().getFirst().getId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(deaneryRepository).findAll(captor.capture());
        assertEquals(deaneryId, captor.getValue().getFilters().get("id"));
    }

    @Test
    void updateShouldValidateAndDelegateToRepository() {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        DeaneryUpdateRequest request = new DeaneryUpdateRequest();
        request.setName("Updated");
        Deanery existing = deanery(deaneryId, diocese(dioceseId));
        existing.setName("Old");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryValidationHelper.requireExistingDeaneryWithDiocese(deaneryId)).thenReturn(existing, existing);
        when(deaneryValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(deaneryRepository.update(deaneryId, request, "LEADER1")).thenAnswer(invocation -> {
            existing.setName("Updated");
            return existing;
        });

        DeaneryResponse result = deaneryService.update(deaneryId, request, authentication);

        assertEquals("Updated", existing.getName());
        assertEquals(deaneryId, result.getId());
        verify(deaneryValidationHelper).validateUpdateInput(request);
        verify(deaneryValidationHelper).validateUpdateNameUniqueness(deaneryId, "Updated", existing);
    }

    @Test
    void deleteShouldValidateScopeAndDelegateToRepository() {
        UUID deaneryId = UUID.randomUUID();
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryValidationHelper.requireExistingDeanery(deaneryId)).thenReturn(deanery);

        deaneryService.delete(deaneryId, authentication);

        verify(deaneryScopeHelper).enforceDeleteScope(authentication, deanery);
        verify(deaneryRepository).delete(deaneryId);
    }

    @Test
    void createShouldWrapDataConflict() {
        UUID dioceseId = UUID.randomUUID();
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setName("Deanery");
        request.setAssociationName("Association");
        request.setDioceseId(dioceseId);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(deaneryValidationHelper.requireExistingDiocese(dioceseId)).thenReturn(diocese(dioceseId));
        when(deaneryRepository.save(any(Deanery.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> deaneryService.create(request, authentication));

        assertEquals("Dữ liệu giáo hạt bị trùng hoặc xung đột với dữ liệu hiện có", exception.getMessage());
    }

    private Diocese diocese(UUID dioceseId) {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        return diocese;
    }

    private Deanery deanery(UUID deaneryId, Diocese diocese) {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setName("Sample Deanery");
        deanery.setAssociationName("Sample Association");
        deanery.setDiocese(diocese);
        return deanery;
    }
}
