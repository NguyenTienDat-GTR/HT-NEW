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
import phucuong.ht.common.utils.OrganizationUnitCodeGenerator;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeaneryServiceTest {

    @Mock
    private DeaneryRepository deaneryRepository;

    @Mock
    private OrganizationUnitCodeGenerator organizationUnitCodeGenerator;

    @Mock
    private securityUtils securityUtils;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private DeaneryService deaneryService;

    @Test
    void createShouldDelegateToRepository() {
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setName("Deanery");
        request.setAssociationName("Association");
        request.setDioceseCode("DIO1");

        Diocese diocese = diocese("DIO1");
        Deanery created = new Deanery();
        created.setDeaneryCode("dio1-association");
        created.setAssociationName("Association");
        created.setDiocese(diocese);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_ALL)).thenReturn(true);
        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");
        when(organizationUnitCodeGenerator.generateDeaneryCode(eq("DIO1"), eq("Association"), any())).thenReturn("dio1-association");
        when(deaneryRepository.getDioceseReference("DIO1")).thenReturn(diocese);
        when(deaneryRepository.save(argThat(entity ->
                "dio1-association".equals(entity.getDeaneryCode())
                        && "Association".equals(entity.getAssociationName())
                        && "DIO1".equals(entity.getDiocese().getDioceseCode())
                        && "LEADER1".equals(entity.getCreatedBy())
                        && "LEADER1".equals(entity.getUpdatedBy())
        ))).thenReturn(created);
        when(deaneryRepository.findByIdWithDiocese("dio1-association")).thenReturn(Optional.of(created));

        DeaneryResponse result = deaneryService.create(request, authentication);

        assertEquals("dio1-association", result.getDeaneryCode());
        assertEquals("DIO1", result.getDioceseCode());
    }

    @Test
    void findAllShouldApplyDioceseScopeFilter() {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setFilters(new LinkedHashMap<>(java.util.Map.of("name", "ignored")));

        Deanery summary = new Deanery();
        summary.setDeaneryCode("DEA1");
        Deanery hydrated = new Deanery();
        hydrated.setDeaneryCode("DEA1");
        hydrated.setDiocese(diocese("DIO1"));
        Page<Deanery> page = new PageImpl<>(List.of(summary));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.of("DIO1"));
        when(deaneryRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(deaneryRepository.findAllByDeaneryCodesWithDiocese(List.of("DEA1"))).thenReturn(List.of(hydrated));

        Page<DeaneryResponse> result = deaneryService.findAll(request, authentication);

        assertEquals("DEA1", result.getContent().getFirst().getDeaneryCode());
        assertEquals("DIO1", result.getContent().getFirst().getDioceseCode());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(deaneryRepository).findAll(captor.capture());
        assertEquals("DIO1", captor.getValue().getFilters().get("diocese.dioceseCode"));
        assertEquals("ignored", captor.getValue().getFilters().get("name"));
    }

    @Test
    void findAllShouldApplyDeaneryScopeFilter() {
        BaseSearchRequest request = new BaseSearchRequest();

        Deanery summary = new Deanery();
        summary.setDeaneryCode("DEA1");
        Deanery hydrated = new Deanery();
        hydrated.setDeaneryCode("DEA1");
        hydrated.setDiocese(diocese("DIO1"));
        Page<Deanery> page = new PageImpl<>(List.of(summary));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DIOCESE)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DEANERY)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-deanery");
        when(accountRepository.findActiveDeaneryCodeByUsername("admin-deanery")).thenReturn(Optional.of("DEA1"));
        when(deaneryRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(deaneryRepository.findAllByDeaneryCodesWithDiocese(List.of("DEA1"))).thenReturn(List.of(hydrated));

        Page<DeaneryResponse> result = deaneryService.findAll(request, authentication);

        assertEquals("DEA1", result.getContent().getFirst().getDeaneryCode());
        assertEquals("DIO1", result.getContent().getFirst().getDioceseCode());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(deaneryRepository).findAll(captor.capture());
        assertEquals("DEA1", captor.getValue().getFilters().get("deaneryCode"));
    }

    @Test
    void findByIdShouldLoadRequiredEntity() {
        Deanery deanery = new Deanery();
        deanery.setDeaneryCode("DEA1");
        deanery.setDiocese(diocese("DIO1"));

        when(deaneryRepository.findByIdWithDiocese("DEA1")).thenReturn(Optional.of(deanery));

        DeaneryResponse result = deaneryService.findById("DEA1");

        assertEquals("DEA1", result.getDeaneryCode());
        assertEquals("DIO1", result.getDioceseCode());
    }

    @Test
    void updateShouldDelegateToRepository() {
        DeaneryUpdateRequest request = new DeaneryUpdateRequest();
        request.setName("Updated");
        Deanery existing = new Deanery();
        existing.setDeaneryCode("DEA1");
        Deanery updated = new Deanery();
        updated.setDeaneryCode("DEA1");
        updated.setDiocese(diocese("DIO1"));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryRepository.findById("DEA1")).thenReturn(Optional.of(existing));
        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");
        when(deaneryRepository.update("DEA1", request, "LEADER1")).thenReturn(updated);
        when(deaneryRepository.findByIdWithDiocese("DEA1")).thenReturn(Optional.of(updated));

        DeaneryResponse result = deaneryService.update("DEA1", request, authentication);

        assertEquals("DEA1", result.getDeaneryCode());
        assertEquals("DIO1", result.getDioceseCode());
    }

    @Test
    void deleteShouldDelegateToRepositoryForSystemWidePermission() {
        Deanery deanery = new Deanery();
        deanery.setDeaneryCode("DEA1");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryRepository.findById("DEA1")).thenReturn(Optional.of(deanery));
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_ALL)).thenReturn(true);

        deaneryService.delete("DEA1", authentication);

        verify(deaneryRepository).delete("DEA1");
    }

    @Test
    void deleteShouldEnforceDioceseScope() {
        Deanery deanery = new Deanery();
        deanery.setDeaneryCode("DEA1");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryRepository.findById("DEA1")).thenReturn(Optional.of(deanery));
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.of("DIO1"));
        when(deaneryRepository.findDioceseCodeByDeaneryCode("DEA1")).thenReturn(Optional.of("DIO2"));

        ApiException exception = assertThrows(ApiException.class, () -> deaneryService.delete("DEA1", authentication));

        assertEquals("Không thể xóa giáo hạt ngoài phạm vi giáo phận hiện tại", exception.getMessage());
    }

    @Test
    void findByIdShouldThrowApiExceptionWhenMissing() {
        when(deaneryRepository.findByIdWithDiocese("DEA1")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> deaneryService.findById("DEA1"));

        assertEquals("Không tìm thấy giáo hạt: DEA1", exception.getMessage());
    }

    @Test
    void updateShouldThrowValidationErrorWhenCodeBlank() {
        ApiException exception = assertThrows(ApiException.class, () -> deaneryService.update(" ", new DeaneryUpdateRequest(), null));

        assertEquals("Mã giáo hạt là bắt buộc", exception.getMessage());
    }

    @Test
    void createShouldWrapDataConflict() {
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setName("Deanery");
        request.setAssociationName("Association");
        request.setDioceseCode("DIO1");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_ALL)).thenReturn(true);
        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");
        when(organizationUnitCodeGenerator.generateDeaneryCode(eq("DIO1"), eq("Association"), any())).thenReturn("dio1-association");
        when(deaneryRepository.getDioceseReference("DIO1")).thenReturn(new Diocese());
        when(deaneryRepository.save(any(Deanery.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> deaneryService.create(request, authentication));

        assertEquals("Dữ liệu giáo hạt bị trùng hoặc xung đột với dữ liệu hiện có", exception.getMessage());
    }

    @Test
    void deleteShouldWrapReferenceConflict() {
        Deanery deanery = new Deanery();
        deanery.setDeaneryCode("DEA1");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(deaneryRepository.findById("DEA1")).thenReturn(Optional.of(deanery));
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_ALL)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("fk")).when(deaneryRepository).delete("DEA1");

        ApiException exception = assertThrows(ApiException.class, () -> deaneryService.delete("DEA1", authentication));

        assertEquals("Không thể xóa giáo hạt vì đang được dữ liệu khác tham chiếu", exception.getMessage());
    }

    private Diocese diocese(String dioceseCode) {
        Diocese diocese = new Diocese();
        diocese.setDioceseCode(dioceseCode);
        return diocese;
    }
}
