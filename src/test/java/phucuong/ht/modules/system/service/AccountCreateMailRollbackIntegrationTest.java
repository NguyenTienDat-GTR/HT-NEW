package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Gửi email mật khẩu tạm lỗi thì tài khoản và vai trò vừa ghi phải bị rollback trên DB thật.
 */
@SpringBootTest
class AccountCreateMailRollbackIntegrationTest {

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    @MockitoBean
    private AccountCredentialMailService mailService;

    @Test
    void mailFailureShouldRollbackCreatedAccountAndRoles() {
        String localPart = "rollbackit" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        AccountCreateRequest request = new AccountCreateRequest();
        request.setRoleCode("SUPER_ADMIN");
        request.setCredentialEmail(localPart + "@example.com");
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR, "SMTP down"))
                .when(mailService).sendInitialCredentials(anyString(), anyString(), anyString(), anyString());
        long accountsBefore = accountRepository.count();

        try {
            ApiException exception = assertThrows(ApiException.class,
                    () -> accountManagementService.create(request, superAdmin()));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
            assertFalse(accountRepository.existsByUsername(localPart), "Tài khoản phải được rollback khi gửi email lỗi");
            assertTrue(accountRoleRepository.findByAccountUsername(localPart).isEmpty(), "Vai trò phải được rollback khi gửi email lỗi");
            assertEquals(accountsBefore, accountRepository.count());
        } finally {
            // Phòng trường hợp rollback hỏng: không để lại dữ liệu rác trong DB.
            accountRoleRepository.deleteAll(accountRoleRepository.findByAccountUsername(localPart));
            accountRepository.findById(localPart).ifPresent(accountRepository::delete);
        }
    }

    private Authentication superAdmin() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("it-rollback-superadmin", null, List.of("SUPER_ADMIN"), List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
