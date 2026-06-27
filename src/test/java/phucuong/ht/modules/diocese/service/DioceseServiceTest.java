package phucuong.ht.modules.diocese.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DioceseServiceTest {

    @Mock
    private DioceseRepository dioceseRepository;

    @Mock
    private OrganizationUnitCodeGenerator organizationUnitCodeGenerator;

    @Mock
    private securityUtils securityUtils;

    @InjectMocks
    private DioceseService dioceseService;

    @Test
    void createShouldDelegateToRepository() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("Phu Cuong");
        request.setUnionName("Phu Cuong");

        Diocese created = new Diocese();
        created.setDioceseCode("DIO1");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");
        when(organizationUnitCodeGenerator.generateDioceseCode(eq("Phu Cuong"), any())).thenReturn("phu-cuong");
        when(dioceseRepository.save(argThat(entity ->
                "phu-cuong".equals(entity.getDioceseCode())
                        && "Phu Cuong".equals(entity.getUnionName())
                        && "LEADER1".equals(entity.getCreatedBy())
                        && "LEADER1".equals(entity.getUpdatedBy())
        ))).thenReturn(created);

        Diocese result = dioceseService.create(request, authentication);

        assertSame(created, result);
    }

    @Test
    void findAllShouldDelegateToRepository() {
        BaseSearchRequest request = new BaseSearchRequest();
        Page<Diocese> page = new PageImpl<>(List.of(new Diocese()));

        when(dioceseRepository.findAll(request)).thenReturn(page);

        Page<Diocese> result = dioceseService.findAll(request);

        assertSame(page, result);
    }

    @Test
    void findByIdShouldLoadRequiredEntity() {
        Diocese diocese = new Diocese();
        diocese.setDioceseCode("DIO1");

        when(dioceseRepository.findById("DIO1")).thenReturn(Optional.of(diocese));

        Diocese result = dioceseService.findById("DIO1");

        assertSame(diocese, result);
    }

    @Test
    void updateShouldDelegateToRepository() {
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        Diocese updated = new Diocese();
        updated.setDioceseCode("DIO1");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.findById("DIO1")).thenReturn(Optional.of(updated));
        when(dioceseRepository.update("DIO1", request, "LEADER1")).thenReturn(updated);

        Diocese result = dioceseService.update("DIO1", request, authentication);

        assertSame(updated, result);
    }

    @Test
    void deleteShouldDelegateToRepository() {
        Diocese diocese = new Diocese();
        diocese.setDioceseCode("DIO1");
        when(dioceseRepository.findById("DIO1")).thenReturn(Optional.of(diocese));

        dioceseService.delete("DIO1");

        verify(dioceseRepository).delete("DIO1");
    }

    @Test
    void findByIdShouldThrowApiExceptionWhenMissing() {
        when(dioceseRepository.findById("DIO1")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.findById("DIO1"));

        assertEquals("Diocese not found: DIO1", exception.getMessage());
    }

    @Test
    void updateShouldThrowValidationErrorWhenCodeBlank() {
        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.update(" ", new DioceseUpdateRequest(), null));

        assertEquals("Diocese code is required", exception.getMessage());
    }

    @Test
    void createShouldWrapDataConflict() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("Phu Cuong");
        request.setUnionName("Phu Cuong");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");
        when(organizationUnitCodeGenerator.generateDioceseCode(eq("Phu Cuong"), any())).thenReturn("phu-cuong");
        when(dioceseRepository.save(argThat(entity ->
                "phu-cuong".equals(entity.getDioceseCode()) && "Phu Cuong".equals(entity.getUnionName())
        ))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.create(request, authentication));

        assertEquals("Diocese data conflicts with existing records", exception.getMessage());
    }

    @Test
    void deleteShouldWrapReferenceConflict() {
        Diocese diocese = new Diocese();
        diocese.setDioceseCode("DIO1");
        when(dioceseRepository.findById("DIO1")).thenReturn(Optional.of(diocese));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(dioceseRepository).delete("DIO1");

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.delete("DIO1"));

        assertEquals("Cannot delete diocese because it is referenced by other records", exception.getMessage());
    }
}
