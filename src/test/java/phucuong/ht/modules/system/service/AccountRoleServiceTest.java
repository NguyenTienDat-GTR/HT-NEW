package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRoleServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AccountRoleService accountRoleService;

    @Test
    void assignRoleShouldAllowSuperAdminToAssignAdminDiocese() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, helper);

        Account account = new Account();
        account.setUsername("diocese-admin");
        Role targetRole = new Role();
        targetRole.setRoleCode("ADMIN_DIOCESE");
        targetRole.setDisplayOrder(100);

        AccountRoleCreateRequest request = new AccountRoleCreateRequest();
        request.setUsername("diocese-admin");
        request.setRoleCode("ADMIN_DIOCESE");

        when(accountRepository.findById("diocese-admin")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(targetRole));
        when(accountRoleRepository.save(org.mockito.ArgumentMatchers.any(AccountRole.class))).thenAnswer(invocation -> {
            AccountRole saved = invocation.getArgument(0);
            saved.setId("role-id");
            return saved;
        });

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("super-admin", null, List.of("SUPER_ADMIN"), List.of("system.account_role.assign.all"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        var response = accountRoleService.assignRole(request, authentication);

        assertEquals("ADMIN_DIOCESE", response.getRoleCode());
        ArgumentCaptor<AccountRole> captor = ArgumentCaptor.forClass(AccountRole.class);
        verify(accountRoleRepository).save(captor.capture());
        assertEquals("super-admin", captor.getValue().getAssignedBy());
    }
}
