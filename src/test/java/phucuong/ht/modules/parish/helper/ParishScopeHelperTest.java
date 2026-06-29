package phucuong.ht.modules.parish.helper;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.repository.ParishRepository;
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
    private final ParishRepository parishRepository = mock(ParishRepository.class);
    private final DeaneryRepository deaneryRepository = mock(DeaneryRepository.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(securityUtils);
    private final ParishScopeHelper helper = new ParishScopeHelper(
            securityUtils,
            accountRepository,
            parishRepository,
            deaneryRepository,
            commonValidationHelper
    );

    @Test
    void enforceCreateScopeShouldRejectOutsideActiveDeanery() {
        UUID activeDeaneryId = UUID.randomUUID();
        ParishCreateRequest request = new ParishCreateRequest();
        request.setDeaneryId(UUID.randomUUID());
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_DIOCESE)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_DEANERY)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-deanery");
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(activeDeaneryId));

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceCreateScope(request, authentication));

        assertEquals("Không thể tạo giáo xứ ngoài phạm vi giáo hạt hiện tại", exception.getMessage());
    }

    @Test
    void enforceDeleteScopeShouldRejectOutsideActiveDiocese() {
        UUID activeDioceseId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        Parish parish = new Parish();
        parish.setId(parishId);
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.DELETE_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.DELETE_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(activeDioceseId));
        when(parishRepository.findDioceseIdByParishId(parishId)).thenReturn(Optional.of(UUID.randomUUID()));

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceDeleteScope(authentication, parish));

        assertEquals("Không thể xóa giáo xứ ngoài phạm vi giáo phận hiện tại", exception.getMessage());
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

        when(securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_PARISH)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceDeaneryChangeScope(authentication, parish, targetDeanery));

        assertEquals("Tài khoản giáo xứ không được thay đổi giáo hạt của giáo xứ", exception.getMessage());
    }

    @Test
    void getRequiredActiveParishIdShouldRejectMissingUsername() {
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentUsername(authentication)).thenReturn(" ");

        ApiException exception = assertThrows(ApiException.class, () -> helper.getRequiredActiveParishId(authentication, ParishAccessEvaluator.READ_PARISH));

        assertEquals("Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + ParishAccessEvaluator.READ_PARISH, exception.getMessage());
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
