package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
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
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.common.model.request.StatusToggleRequest;

import java.time.LocalDate;
import java.util.List;
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
    @Mock phucuong.ht.modules.auth.service.AccountEmailResolver emailResolver;
    @Mock Authentication authentication;

    @org.mockito.Mock
    private phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

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
        verify(accountRepository).saveAndFlush(savedAccount.capture());
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
    void createShouldFlushDatabaseWritesBeforeSendingEmail() {
        Diocese diocese = diocese("Phú Cường");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_DIOCESE");
        request.setDioceseId(diocese.getId());
        request.setCredentialEmail("admin@phucuong.vn");
        stubCreate(diocese, adminDioceseRole());
        when(validation.normalizeRequiredText("admin@phucuong.vn")).thenReturn("admin@phucuong.vn");
        when(accountRepository.existsByUsername("phucuongadmin")).thenReturn(false);

        service.create(request, authentication);

        var order = org.mockito.Mockito.inOrder(accountRepository, accountRoleRepository, mailService);
        order.verify(accountRepository).saveAndFlush(any(Account.class));
        order.verify(accountRoleRepository).flush();
        order.verify(mailService).sendInitialCredentials(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createShouldPropagateMailFailureSoTransactionRollsBack() {
        Diocese diocese = diocese("Phú Cường");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_DIOCESE");
        request.setDioceseId(diocese.getId());
        request.setCredentialEmail("admin@phucuong.vn");
        stubCreate(diocese, adminDioceseRole());
        when(validation.normalizeRequiredText("admin@phucuong.vn")).thenReturn("admin@phucuong.vn");
        when(accountRepository.existsByUsername("phucuongadmin")).thenReturn(false);
        var mailFailure = new phucuong.ht.common.exception.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                phucuong.ht.common.exception.errorCode.INTERNAL_ERROR, "SMTP down");
        org.mockito.Mockito.doThrow(mailFailure).when(mailService)
                .sendInitialCredentials(anyString(), anyString(), anyString(), anyString());

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.create(request, authentication));

        org.junit.jupiter.api.Assertions.assertSame(mailFailure, thrown);
    }

    @Test
    void createLeaderAccountShouldGenerateNormalizedUsernameAndSuffix() {
        Diocese diocese = diocese("Phú Cường");
        Leader leader = leader(diocese);
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setLeaderId(leader.getId());
        when(validation.normalizeOptionalText("ADMIN_PARISH")).thenReturn("ADMIN_PARISH");
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_PARISH")).thenReturn(Optional.of(role("ADMIN_PARISH")));
        when(leaderRepository.findByIdWithParish(leader.getId())).thenReturn(Optional.of(leader));
        when(validation.normalizeRequiredText(leader.getEmail())).thenReturn(leader.getEmail());
        when(validation.requireAuditActor(any(), anyString())).thenReturn("superadmin");
        when(accountRepository.existsByUsername("anchanhtoa107")).thenReturn(true);
        when(accountRepository.existsByUsername("anchanhtoa1072")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
    void createDioceseAdminShouldRejectWhenDioceseAlreadyHasActiveAdmin() {
        Diocese diocese = diocese("Phú Cường");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("ADMIN_DIOCESE");
        request.setDioceseId(diocese.getId());
        request.setCredentialEmail("admin@phucuong.vn");
        when(validation.normalizeOptionalText("ADMIN_DIOCESE")).thenReturn("ADMIN_DIOCESE");
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

    @Test
    void createLeaderAccountShouldAssignMultipleRolesAndExactlyOnePrimaryRole() {
        Diocese diocese = diocese("Phú Cường");
        Leader leader = leader(diocese);
        Role roleA = role("ROLE_A");
        Role roleB = role("ROLE_B");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCodes(List.of("ROLE_A", "ROLE_B"));
        request.setPrimaryRoleCode("ROLE_B");
        request.setLeaderId(leader.getId());

        when(validation.normalizeOptionalText("ROLE_A")).thenReturn("ROLE_A");
        when(validation.normalizeOptionalText("ROLE_B")).thenReturn("ROLE_B");
        when(roleRepository.findByRoleCodeAndStatusTrue("ROLE_A")).thenReturn(Optional.of(roleA));
        when(roleRepository.findByRoleCodeAndStatusTrue("ROLE_B")).thenReturn(Optional.of(roleB));
        when(leaderRepository.findByIdWithParish(leader.getId())).thenReturn(Optional.of(leader));
        when(validation.normalizeRequiredText(leader.getEmail())).thenReturn(leader.getEmail());
        when(validation.requireAuditActor(any(), anyString())).thenReturn("admin");
        when(accountRepository.existsByUsername("anchanhtoa107")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRoleRepository.save(any(AccountRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request, authentication);

        assertEquals("anchanhtoa107", response.getUsername());
        ArgumentCaptor<AccountRole> assignedRoles = ArgumentCaptor.forClass(AccountRole.class);
        verify(accountRoleRepository, org.mockito.Mockito.times(2)).save(assignedRoles.capture());
        assertEquals(1, assignedRoles.getAllValues().stream().filter(role -> Boolean.TRUE.equals(role.getIsPrimary())).count());
        assertEquals("ROLE_B", assignedRoles.getAllValues().stream()
                .filter(role -> Boolean.TRUE.equals(role.getIsPrimary()))
                .map(role -> role.getRole().getRoleCode())
                .findFirst()
                .orElse(null));
    }

    @Test
    void permissionTargetsShouldUseAccountPermissionScopeAndUsernameSearch() {
        Diocese actorDiocese = diocese("Phú Cường");
        Diocese otherDiocese = diocese("Xuân Lộc");
        Account target = account("target-user", leader(actorDiocese));
        Account outside = account("outside-user", leader(otherDiocese));
        Account current = account("admin-diocese", leader(actorDiocese));
        Role adminDeanery = role("ADMIN_DEANERY");
        var auth = authentication("admin-diocese", List.of("ADMIN_DIOCESE"), List.of("system.account_permission.manage.diocese"));

        when(validation.normalizeOptionalText("target")).thenReturn("target");
        when(accountRepository.findAll()).thenReturn(List.of(target, outside, current));
        when(authorizationHelper.canManageForPermissions(auth, target)).thenReturn(true);
        when(authorizationHelper.canManageForPermissions(auth, outside)).thenReturn(false);
        when(authorizationHelper.canManageForPermissions(auth, current)).thenReturn(false);
        when(accountRoleRepository.findActiveByUsername("target-user")).thenReturn(List.of(accountRole(target, adminDeanery, true, true)));

        var response = service.findAccountPermissionTargets(auth, "target");

        assertEquals(1, response.size());
        assertEquals("target-user", response.get(0).getUsername());
    }

    @Test
    void superAdminCreatesSystemWideRoleAccountFromCredentialEmailEvenWhenEmailIsShared() {
        Role custom = customRole("QUAN_TRI_NOI_DUNG", "super-admin");
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode(custom.getRoleCode());
        request.setCredentialEmail("admin@phucuong.vn");
        Authentication superAdmin = authentication("superadminpc", List.of("SUPER_ADMIN"), List.of());
        when(validation.normalizeOptionalText(custom.getRoleCode())).thenReturn(custom.getRoleCode());
        when(roleRepository.findByRoleCodeAndStatusTrue(custom.getRoleCode())).thenReturn(Optional.of(custom));
        when(validation.normalizeRequiredText("admin@phucuong.vn")).thenReturn("admin@phucuong.vn");
        when(validation.requireAuditActor(any(), anyString())).thenReturn("super-admin");
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Tên đăng nhập "admin" đã có (email trùng với tài khoản khác) nên sinh hậu tố, không báo trùng email.
        when(accountRepository.existsByUsername("admin")).thenReturn(true);
        when(accountRepository.existsByUsername("admin2")).thenReturn(false);

        var response = service.create(request, superAdmin);

        assertEquals("admin2", response.getUsername());
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(saved.capture());
        assertEquals("admin@phucuong.vn", saved.getValue().getEmail());
        assertNull(saved.getValue().getLeader());
        assertNull(saved.getValue().getDiocese());
        verify(mailService).sendInitialCredentials(
                org.mockito.ArgumentMatchers.eq("admin@phucuong.vn"), anyString(),
                org.mockito.ArgumentMatchers.eq("admin2"), anyString());
    }

    @Test
    void superAdminCannotCreateAccountForDeaneryParishOrDioceseOwnedRole() {
        Authentication superAdmin = authentication("superadminpc", List.of("SUPER_ADMIN"), List.of());
        when(validation.validationError(anyString())).thenAnswer(invocation ->
                new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        phucuong.ht.common.exception.errorCode.VALIDATION_ERROR, invocation.getArgument(0)));
        for (Role blocked : List.of(systemRole("ADMIN_DEANERY"), systemRole("ADMIN_PARISH"), customRole("LD_TRUONG", "phucuongadmin"))) {
            AccountCreateRequest request = new AccountCreateRequest();
            request.setRoleCode(blocked.getRoleCode());
            request.setCredentialEmail("x@phucuong.vn");
            when(validation.normalizeOptionalText(blocked.getRoleCode())).thenReturn(blocked.getRoleCode());
            when(roleRepository.findByRoleCodeAndStatusTrue(blocked.getRoleCode())).thenReturn(Optional.of(blocked));

            assertThrows(ApiException.class, () -> service.create(request, superAdmin));
        }
    }

    @Test
    void onlySuperAdminOwnedNonSystemRolesCountAsSystemWide() {
        assertTrue(AccountManagementAuthorizationHelper.isSystemWideCustomRole(customRole("A", "super-admin")));
        assertTrue(!AccountManagementAuthorizationHelper.isSystemWideCustomRole(customRole("B", "phucuongadmin")));
        assertTrue(!AccountManagementAuthorizationHelper.isSystemWideCustomRole(systemRole("ADMIN_PARISH")));
        assertTrue(!AccountManagementAuthorizationHelper.isSystemWideCustomRole(null));
    }

    private Role customRole(String code, String createdBy) {
        Role role = role(code);
        role.setIsSystem(false);
        role.setCreatedBy(createdBy);
        return role;
    }

    private Role systemRole(String code) {
        Role role = role(code);
        role.setIsSystem(true);
        role.setCreatedBy("SYSTEM");
        return role;
    }

    private void stubCreate(Diocese diocese, Role role) {
        when(validation.normalizeOptionalText(role.getRoleCode())).thenReturn(role.getRoleCode());
        when(roleRepository.findByRoleCodeAndStatusTrue(role.getRoleCode())).thenReturn(Optional.of(role));
        when(dioceseRepository.findByIdForUpdate(diocese.getId())).thenReturn(Optional.of(diocese));
        when(accountRepository.existsAdminDioceseByDioceseId(diocese.getId())).thenReturn(false);
        when(validation.requireAuditActor(any(), anyString())).thenReturn("superadmin");
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Role adminDioceseRole() { return role("ADMIN_DIOCESE"); }

    private Role role(String code) {
        Role role = new Role();
        role.setRoleCode(code);
        role.setStatus(true);
        role.setDisplayOrder(switch (code) {
            case "SUPER_ADMIN" -> 0;
            case "ADMIN_DIOCESE" -> 10;
            case "ADMIN_DEANERY" -> 20;
            case "ADMIN_PARISH" -> 30;
            default -> 40;
        });
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

    private Account account(String username, Leader leader) {
        Account account = new Account();
        account.setUsername(username);
        account.setLeader(leader);
        account.setStatus(true);
        return account;
    }

    private AccountRole accountRole(Account account, Role role, boolean status, boolean primary) {
        AccountRole accountRole = new AccountRole();
        accountRole.setId(UUID.randomUUID().toString());
        accountRole.setAccount(account);
        accountRole.setRole(role);
        accountRole.setStatus(status);
        accountRole.setIsPrimary(primary);
        accountRole.setAssignedAt(java.time.OffsetDateTime.now());
        return accountRole;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // ---- Admin đặt lại mật khẩu

    @Test
    void resetPasswordShouldStoreTemporaryPasswordMailItAndOnlyThenRevokeSessions() {
        Account target = resetTarget();
        stubResetActor();
        when(accountRepository.findById("parish.admin")).thenReturn(Optional.of(target));
        when(emailResolver.resolve(target)).thenReturn("parish.admin@gmail.com");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        var response = service.resetPassword("parish.admin", authentication);

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.time.OffsetDateTime> expiresAt = ArgumentCaptor.forClass(java.time.OffsetDateTime.class);
        verify(mailService).sendResetPassword(
                org.mockito.ArgumentMatchers.eq("parish.admin@gmail.com"),
                org.mockito.ArgumentMatchers.eq("parish.admin"),
                org.mockito.ArgumentMatchers.eq("parish.admin"),
                raw.capture(), expiresAt.capture(),
                org.mockito.ArgumentMatchers.eq("admin.deanery"));
        assertTrue(raw.getValue().matches("[A-Za-z]{8}"), "Mật khẩu tạm gồm đúng 8 chữ cái: " + raw.getValue());
        verify(passwordEncoder).encode(raw.getValue());
        assertEquals("new-hash", target.getPassword());
        assertTrue(target.getMustChangePassword());
        assertEquals(expiresAt.getValue(), target.getTemporaryPasswordExpiresAt());
        long minutes = java.time.Duration.between(java.time.OffsetDateTime.now(), expiresAt.getValue()).toMinutes();
        assertTrue(minutes >= 9 && minutes <= 10, "Hạn mật khẩu tạm 10 phút, thực tế còn " + minutes);
        assertEquals("pa***@g***.com", response.getMaskedEmail());
        assertEquals("parish.admin", response.getUsername());
        var order = org.mockito.Mockito.inOrder(accountRepository, mailService, redisTokenSessionService);
        order.verify(accountRepository).saveAndFlush(target);
        order.verify(mailService).sendResetPassword(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        order.verify(redisTokenSessionService).revokeAllSessions("parish.admin");
    }

    @Test
    void resetPasswordShouldNotRevokeSessionsWhenMailFailsSoTransactionRollsBack() {
        Account target = resetTarget();
        stubResetActor();
        when(accountRepository.findById("parish.admin")).thenReturn(Optional.of(target));
        when(emailResolver.resolve(target)).thenReturn("parish.admin@gmail.com");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        org.mockito.Mockito.doThrow(new ApiException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        phucuong.ht.common.exception.errorCode.INTERNAL_ERROR, "SMTP down"))
                .when(mailService).sendResetPassword(anyString(), anyString(), anyString(), anyString(), any(), anyString());

        ApiException exception = assertThrows(ApiException.class, () -> service.resetPassword("parish.admin", authentication));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(redisTokenSessionService, org.mockito.Mockito.never()).revokeAllSessions(anyString());
    }

    @Test
    void resetPasswordShouldRejectAccountWithoutAnyEmail() {
        Account target = resetTarget();
        when(accountRepository.findById("parish.admin")).thenReturn(Optional.of(target));
        when(emailResolver.resolve(target)).thenReturn(null);
        when(validation.validationError(anyString())).thenReturn(new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                phucuong.ht.common.exception.errorCode.VALIDATION_ERROR, "no email"));

        assertThrows(ApiException.class, () -> service.resetPassword("parish.admin", authentication));

        verify(accountRepository, org.mockito.Mockito.never()).saveAndFlush(any(Account.class));
        verify(passwordEncoder, org.mockito.Mockito.never()).encode(anyString());
    }

    @Test
    void resetPasswordShouldRejectLockedAccount() {
        Account target = resetTarget();
        target.setStatus(false);
        when(accountRepository.findById("parish.admin")).thenReturn(Optional.of(target));
        when(validation.validationError(anyString())).thenReturn(new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                phucuong.ht.common.exception.errorCode.VALIDATION_ERROR, "locked"));

        assertThrows(ApiException.class, () -> service.resetPassword("parish.admin", authentication));

        verify(mailService, org.mockito.Mockito.never())
                .sendResetPassword(anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void resetPasswordShouldStopBeforeAnyWriteWhenScopeCheckFails() {
        Account target = resetTarget();
        when(accountRepository.findById("parish.admin")).thenReturn(Optional.of(target));
        org.mockito.Mockito.doThrow(new ApiException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        phucuong.ht.common.exception.errorCode.ACCESS_DENIED, "out of scope"))
                .when(authorizationHelper).requireCanResetPassword(authentication, target);

        ApiException exception = assertThrows(ApiException.class, () -> service.resetPassword("parish.admin", authentication));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatus());
        verify(accountRepository, org.mockito.Mockito.never()).saveAndFlush(any(Account.class));
        verify(passwordEncoder, org.mockito.Mockito.never()).encode(anyString());
        verify(mailService, org.mockito.Mockito.never())
                .sendResetPassword(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        verify(redisTokenSessionService, org.mockito.Mockito.never()).revokeAllSessions(anyString());
    }

    @Test
    void resetPasswordShouldUseLeaderNameWhenAccountHasLeader() {
        Account target = resetTarget();
        Leader leader = new Leader();
        leader.setLastName("Nguyễn");
        leader.setFirstName("Phước");
        target.setLeader(leader);
        stubResetActor();
        when(accountRepository.findById("parish.admin")).thenReturn(Optional.of(target));
        when(emailResolver.resolve(target)).thenReturn("leader@gmail.com");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        service.resetPassword("parish.admin", authentication);

        verify(mailService).sendResetPassword(
                org.mockito.ArgumentMatchers.eq("leader@gmail.com"),
                org.mockito.ArgumentMatchers.eq("Nguyễn Phước"),
                org.mockito.ArgumentMatchers.eq("parish.admin"),
                anyString(), any(), anyString());
    }

    private Account resetTarget() {
        Account target = new Account();
        target.setUsername("parish.admin");
        target.setPassword("old-hash");
        target.setStatus(true);
        target.setMustChangePassword(false);
        return target;
    }

    private void stubResetActor() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin.deanery", null, List.of("ADMIN_DEANERY"), List.of());
        when(authentication.getPrincipal()).thenReturn(principal);
        when(validation.requireAuditActor(org.mockito.ArgumentMatchers.eq(authentication), anyString())).thenReturn("leader-id-1");
    }
}
