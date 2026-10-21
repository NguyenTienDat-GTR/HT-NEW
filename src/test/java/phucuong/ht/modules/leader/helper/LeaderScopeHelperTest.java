package phucuong.ht.modules.leader.helper;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderScopeHelperTest {

    private final securityUtils securityUtils = mock(securityUtils.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ParishRepository parishRepository = mock(ParishRepository.class);
    private final CommonValidationHelper validation = new CommonValidationHelper(securityUtils);
    private final LeaderScopeHelper helper = new LeaderScopeHelper(securityUtils, accountRepository, parishRepository, validation);

    @Test
    void enforceCreateScopeShouldRejectOutsideActiveParish() {
        UUID activeParishId = UUID.randomUUID();
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setParishId(UUID.randomUUID());
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_PARISH)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-parish");
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(activeParishId));

        ApiException exception = assertThrows(ApiException.class, () -> helper.enforceCreateScope(request, authentication));

        assertEquals("Không thể tạo Huynh Trưởng ngoài phạm vi giáo xứ hiện tại", exception.getMessage());
    }

    @Test
    void enforceCreateScopeShouldAllowParishInsideActiveDiocese() {
        UUID dioceseId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setParishId(parishId);
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-diocese");
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(dioceseId));
        when(parishRepository.findDioceseIdByParishId(parishId)).thenReturn(Optional.of(dioceseId));

        assertDoesNotThrow(() -> helper.enforceCreateScope(request, authentication));
    }

    @Test
    void enforceUpdateParishScopeShouldRejectMoveOutsideActiveParish() {
        UUID activeParishId = UUID.randomUUID();
        Leader leader = new Leader();
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        leader.setParish(parish);
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-parish");
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(activeParishId));

        ApiException exception = assertThrows(ApiException.class,
                () -> helper.enforceUpdateParishScope(authentication, leader, activeParishId));

        assertEquals("Không thể chuyển Huynh Trưởng ra ngoài giáo xứ hiện tại", exception.getMessage());
    }

    @Test
    void getRequiredActiveParishIdShouldRejectMissingUsername() {
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentUsername(authentication)).thenReturn(" ");

        ApiException exception = assertThrows(ApiException.class,
                () -> helper.getRequiredActiveParishId(authentication, LeaderAccessEvaluator.READ_PARISH));

        assertEquals("Không xác định được tài khoản hiện tại", exception.getMessage());
    }
}
