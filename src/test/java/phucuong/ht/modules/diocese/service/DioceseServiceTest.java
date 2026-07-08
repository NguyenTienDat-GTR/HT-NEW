package phucuong.ht.modules.diocese.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.diocese.helper.DioceseValidationHelper;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DioceseServiceTest {

    @Mock
    private DioceseRepository dioceseRepository;

    @Mock
    private DioceseValidationHelper dioceseValidationHelper;

    @InjectMocks
    private DioceseService dioceseService;

    @Test
    void createShouldValidateAndDelegateToRepository() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("Phu Cuong");
        request.setUnionName("Phu Cuong");
        Diocese created = new Diocese();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.save(argThat(entity ->
                "Phu Cuong".equals(entity.getName())
                        && "Phu Cuong".equals(entity.getUnionName())
                        && "LEADER1".equals(entity.getCreatedBy())
                        && "LEADER1".equals(entity.getUpdatedBy())
        ))).thenReturn(created);

        Diocese result = dioceseService.create(request, authentication);

        assertSame(created, result);
        verify(dioceseValidationHelper).validateCreateInput(request);
        verify(dioceseValidationHelper).validateCreateNameUniqueness("Phu Cuong");
    }

    @Test
    void findAllShouldDelegateToRepository() {
        BaseSearchRequest request = new BaseSearchRequest();
        Page<Diocese> page = new PageImpl<>(List.of(new Diocese()));

        when(dioceseRepository.findAll(request)).thenReturn(page);

        Page<Diocese> result = dioceseService.findAll(request, null);

        assertSame(page, result);
    }

    @Test
    void findByIdShouldLoadRequiredEntityFromHelper() {
        UUID id = UUID.randomUUID();
        Diocese diocese = new Diocese();
        diocese.setId(id);
        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(diocese);

        Diocese result = dioceseService.findById(id, null);

        assertSame(diocese, result);
    }

    @Test
    void updateShouldValidateAndDelegateToRepository() {
        UUID id = UUID.randomUUID();
        Diocese existing = new Diocese();
        existing.setId(id);
        existing.setName("Old Name");
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        request.setName("New Name");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(existing);
        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.update(id, request, "LEADER1")).thenAnswer(invocation -> {
            existing.setName("New Name");
            return existing;
        });

        Diocese result = dioceseService.update(id, request, authentication);

        assertSame(existing, result);
        verify(dioceseValidationHelper).validateUpdateInput(request);
        verify(dioceseValidationHelper).validateUpdateNameUniqueness(id, "New Name", "Old Name");
    }

    @Test
    void deleteShouldValidateAndDelegateToRepository() {
        UUID id = UUID.randomUUID();
        Diocese diocese = new Diocese();
        diocese.setId(id);
        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(diocese);

        dioceseService.delete(id);

        verify(dioceseRepository).delete(id);
    }

    @Test
    void createShouldWrapDataConflict() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("Phu Cuong");
        request.setUnionName("Phu Cuong");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.save(any(Diocese.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.create(request, authentication));

        assertEquals("Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có", exception.getMessage());
    }

    @Test
    void toggleStatusShouldValidateConfirmationAndDelegateToRepository() {
        UUID id = UUID.randomUUID();
        Diocese diocese = new Diocese();
        diocese.setId(id);
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(diocese);
        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.toggleStatus(id, "LEADER1")).thenReturn(diocese);

        Diocese result = dioceseService.toggleStatus(id, request, authentication);

        assertSame(diocese, result);
        verify(dioceseValidationHelper).validateToggleConfirmation("Xác nhận");
        verify(dioceseRepository).toggleStatus(id, "LEADER1");
    }

    @Test
    void toggleStatusShouldPropagateConfirmationFailure() {
        UUID id = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Sai");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(new Diocese());
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "invalid"))
                .when(dioceseValidationHelper)
                .validateToggleConfirmation("Sai");

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.toggleStatus(id, request, authentication));

        assertEquals("invalid", exception.getMessage());
    }
}
