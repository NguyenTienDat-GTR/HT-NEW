package phucuong.ht.modules.parish.helper;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.security.ParishAccessEvaluator;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParishScopeHelperTest {

    private final securityUtils securityUtils = mock(securityUtils.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(securityUtils);
    private final ParishScopeHelper helper = new ParishScopeHelper(
            securityUtils,
            accountRepository,
            commonValidationHelper
    );

    @Test
    void enforceCreateScopeShouldRejectOutsideActiveDeanery() {
        UUID activeDeaneryId = UUID.randomUUID();
        ParishCreateRequest request = new ParishCreateRequest();
        request.setDeaneryId(UUID.randomUUID());
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_DEANERY)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-deanery");
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(activeDeaneryId));

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceCreateScope(request, authentication));

        assertEquals("Khong the tao giao xu ngoai pham vi giao hat hien tai", exception.getMessage());
    }

    @Test
    void enforceDeleteScopeShouldRejectScopedAdmin() {
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.DELETE_ALL)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceDeleteScope(authentication, parish));

        assertEquals("Tai khoan hien tai khong co quyen xoa giao xu", exception.getMessage());
    }

    @Test
    void enforceDeaneryChangeScopeShouldRejectParishScopedMove() {
        UUID currentDeaneryId = UUID.randomUUID();
        UUID targetDeaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setDeanery(deanery(currentDeaneryId, dioceseId));
        Deanery targetDeanery = deanery(targetDeaneryId, dioceseId);
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_ALL)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceDeaneryChangeScope(authentication, parish, targetDeanery));

        assertEquals("Tai khoan hien tai khong co quyen thay doi giao hat cua giao xu", exception.getMessage());
    }

    @Test
    void getRequiredActiveParishIdShouldRejectMissingUsername() {
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentUsername(authentication)).thenReturn(" ");

        ApiException exception = assertThrows(ApiException.class, () -> helper.getRequiredActiveParishId(authentication, ParishAccessEvaluator.READ_PARISH));

        assertEquals("Tai khoan hien tai khong xac dinh duoc ten dang nhap cho quyen " + ParishAccessEvaluator.READ_PARISH, exception.getMessage());
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
