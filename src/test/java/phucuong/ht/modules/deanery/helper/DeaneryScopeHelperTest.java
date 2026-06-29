package phucuong.ht.modules.deanery.helper;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeaneryScopeHelperTest {

    private final securityUtils securityUtils = mock(securityUtils.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final DeaneryRepository deaneryRepository = mock(DeaneryRepository.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(securityUtils);
    private final DeaneryScopeHelper helper = new DeaneryScopeHelper(
            securityUtils,
            accountRepository,
            deaneryRepository,
            commonValidationHelper
    );

    @Test
    void enforceCreateScopeShouldRejectOutsideActiveDiocese() {
        UUID activeDioceseId = UUID.randomUUID();
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setDioceseId(UUID.randomUUID());
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(activeDioceseId));

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceCreateScope(request, authentication));

        assertEquals("Không thể tạo giáo hạt ngoài phạm vi giáo phận hiện tại", exception.getMessage());
    }

    @Test
    void enforceDeleteScopeShouldRejectOutsideActiveDiocese() {
        UUID activeDioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Authentication authentication = mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(activeDioceseId));
        when(deaneryRepository.findDioceseIdByDeaneryId(deaneryId)).thenReturn(Optional.of(UUID.randomUUID()));

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceDeleteScope(authentication, deanery));

        assertEquals("Không thể xóa giáo hạt ngoài phạm vi giáo phận hiện tại", exception.getMessage());
    }

    @Test
    void getRequiredActiveDeaneryIdShouldRejectMissingUsername() {
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentUsername(authentication)).thenReturn(" ");

        ApiException exception = assertThrows(ApiException.class, () -> helper.getRequiredActiveDeaneryId(authentication, DeaneryAccessEvaluator.READ_DEANERY));

        assertEquals("Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + DeaneryAccessEvaluator.READ_DEANERY, exception.getMessage());
    }
}
