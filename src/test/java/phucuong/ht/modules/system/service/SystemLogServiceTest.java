package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.system.repository.SystemLogRepository;
import phucuong.ht.modules.system.security.SystemLogAccessEvaluator;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceTest {

    @Mock
    private SystemLogRepository systemLogRepository;
    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void findAllShouldApplyDioceseScopeFilter() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemLogService service = new SystemLogService(systemLogRepository, accountScopeValidationHelper, helper, new securityUtils());
        UUID dioceseId = UUID.randomUUID();

        when(accountScopeValidationHelper.requireActiveDioceseId(any(), any())).thenReturn(dioceseId);
        when(systemLogRepository.findAll(any())).thenReturn(new PageImpl<>(List.of()));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(SystemLogAccessEvaluator.READ_DIOCESE)
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        service.findAll(new BaseSearchRequest(), authentication);

        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(systemLogRepository).findAll(captor.capture());
        assertEquals(dioceseId, captor.getValue().getFilters().get("dioceseId"));
    }
}
