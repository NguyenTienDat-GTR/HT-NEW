package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.common.model.request.StatusToggleRequest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock AccountRoleRepository accountRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock LeaderRepository leaderRepository;
    @Mock DioceseRepository dioceseRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CommonValidationHelper validation;
    @Mock AccountManagementAuthorizationHelper authorizationHelper;
    @Mock AccountCredentialMailService mailService;
    @Mock RedisTokenSessionService redisTokenSessionService;
    @Mock Authentication authentication;

    @InjectMocks AccountManagementService service;

    @Test
    void createLeaderlessDioceseAdminShouldUseDioceseUsernameAndRequestEmail() {
        Diocese diocese = diocese("Phú Cường");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_DIOCESE");
        request.setDioceseId(diocese.getId());
        request.setCredentialEmail("admin@phucuong.vn");
        stubCreate(diocese, adminDioceseRole());
        when(validation.normalizeRequiredText("admin@phucuong.vn")).thenReturn("admin@phucuong.vn");
        when(accountRepository.existsByUsername("phucuongadmin")).thenReturn(false);

        var response = service.create(request, authentication);

        assertEquals("phucuongadmin", response.getUsername());
        assertNull(response.getLeaderId());
        ArgumentCaptor<Account> savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedAccount.capture());
        assertNotNull(savedAccount.getValue().getCreatedAt());
        assertNotNull(savedAccount.getValue().getUpdatedAt());
        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendInitialCredentials(
                org.mockito.ArgumentMatchers.eq("admin@phucuong.vn"),
                org.mockito.ArgumentMatchers.eq("Quản trị viên Phú Cường"),
                org.mockito.ArgumentMatchers.eq("phucuongadmin"),
                password.capture()
        );
        assertTrue(password.getValue().matches("[A-Za-z0-9]{12}"));
        assertTrue(savedAccount.getValue().getMustChangePassword());
        verify(passwordEncoder).encode(password.getValue());
    }

    @Test
    void createLeaderAccountShouldGenerateNormalizedUsernameAndSuffix() {
        Diocese diocese = diocese("Phú Cường");
        Leader leader = leader(diocese);
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setLeaderId(leader.getId());
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_PARISH")).thenReturn(Optional.of(role("ADMIN_PARISH")));
        when(leaderRepository.findByIdWithParish(leader.getId())).thenReturn(Optional.of(leader));
        when(validation.normalizeRequiredText(leader.getEmail())).thenReturn(leader.getEmail());
        when(validation.requireAuditActor(any(), anyString())).thenReturn("superadmin");
        when(accountRepository.existsByUsername("anchanhtoa107")).thenReturn(true);
        when(accountRepository.existsByUsername("anchanhtoa1072")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request, authentication);

        assertEquals("anchanhtoa1072", response.getUsername());
        verify(mailService).sendInitialCredentials(
                org.mockito.ArgumentMatchers.eq(leader.getEmail()),
                org.mockito.ArgumentMatchers.eq("Nguyễn An"),
                org.mockito.ArgumentMatchers.eq("anchanhtoa1072"),
                org.mockito.ArgumentMatchers.matches("[A-Za-z0-9]{12}")
        );
    }

    @Test
    void createDioceseAdminShouldRejectSecondAccountEvenWhenExistingAccountIsInactive() {
        Diocese diocese = diocese("Phú Cường");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_DIOCESE");
        request.setDioceseId(diocese.getId());
        request.setCredentialEmail("admin@phucuong.vn");
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(adminDioceseRole()));
        when(dioceseRepository.findByIdForUpdate(diocese.getId())).thenReturn(Optional.of(diocese));
        when(accountRepository.existsAdminDioceseByDioceseId(diocese.getId())).thenReturn(true);
        when(validation.conflict(anyString())).thenReturn(new ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                phucuong.ht.common.exception.errorCode.CONFLICT,
                "Mỗi giáo phận chỉ được có một account ADMIN_DIOCESE"
        ));

        assertThrows(ApiException.class, () -> service.create(request, authentication));
    }

    @Test
    void disablingAnAccountShouldRevokeItsExistingSessions() {
        Account account = new Account();
        account.setUsername("admin-parish");
        account.setStatus(true);
        when(accountRepository.findById("admin-parish")).thenReturn(Optional.of(account));
        when(validation.requireAuditActor(any(), anyString())).thenReturn("superadmin");
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("XAC NHAN");

        service.toggleStatus("admin-parish", request, authentication);

        assertTrue(!account.getStatus());
        verify(redisTokenSessionService).revokeAllSessions("admin-parish");
    }

    private void stubCreate(Diocese diocese, Role role) {
        when(roleRepository.findByRoleCodeAndStatusTrue(role.getRoleCode())).thenReturn(Optional.of(role));
        when(dioceseRepository.findByIdForUpdate(diocese.getId())).thenReturn(Optional.of(diocese));
        when(accountRepository.existsAdminDioceseByDioceseId(diocese.getId())).thenReturn(false);
        when(validation.requireAuditActor(any(), anyString())).thenReturn("superadmin");
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Role adminDioceseRole() { return role("ADMIN_DIOCESE"); }

    private Role role(String code) {
        Role role = new Role();
        role.setRoleCode(code);
        role.setStatus(true);
        return role;
    }

    private Diocese diocese(String name) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName(name);
        diocese.setStatus(true);
        return diocese;
    }

    private Leader leader(Diocese diocese) {
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setDiocese(diocese);
        deanery.setStatus(true);
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setName("Chánh Tòa");
        parish.setDeanery(deanery);
        parish.setStatus(true);
        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setFirstName("An");
        leader.setLastName("Nguyễn");
        leader.setBirthDate(LocalDate.of(2000, 7, 10));
        leader.setEmail("an@example.com");
        leader.setParish(parish);
        leader.setStatus(true);
        return leader;
    }
}
