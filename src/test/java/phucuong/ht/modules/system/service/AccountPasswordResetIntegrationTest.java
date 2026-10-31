package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Đặt lại mật khẩu trên DB thật: gửi email mật khẩu tạm lỗi thì mật khẩu cũ phải được giữ nguyên (rollback).
 */
@SpringBootTest
class AccountPasswordResetIntegrationTest {

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AccountCredentialMailService mailService;

    @Test
    void mailFailureShouldKeepOldPasswordAndNotMarkAccountAsTemporary() {
        String username = "resetit" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String oldHash = passwordEncoder.encode("OldPassword1");
        createAccount(username, oldHash);
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR, "SMTP down"))
                .when(mailService).sendResetPassword(anyString(), anyString(), anyString(), anyString(), any(), anyString());

        try {
            ApiException exception = assertThrows(ApiException.class,
                    () -> accountManagementService.resetPassword(username, superAdmin()));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
            Account reloaded = accountRepository.findById(username).orElseThrow();
            assertEquals(oldHash, reloaded.getPassword(), "Mật khẩu cũ phải được giữ nguyên khi gửi email lỗi");
            assertFalse(reloaded.getMustChangePassword());
            assertNull(reloaded.getTemporaryPasswordExpiresAt());
        } finally {
            accountRepository.findById(username).ifPresent(accountRepository::delete);
        }
    }

    @Test
    void successfulResetShouldPersistTemporaryPasswordWithTenMinuteExpiry() {
        String username = "resetok" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String oldHash = passwordEncoder.encode("OldPassword1");
        createAccount(username, oldHash);

        try {
            var response = accountManagementService.resetPassword(username, superAdmin());

            Account reloaded = accountRepository.findById(username).orElseThrow();
            assertTrue(reloaded.getMustChangePassword());
            assertNotNull(reloaded.getTemporaryPasswordExpiresAt());
            assertFalse(passwordEncoder.matches("OldPassword1", reloaded.getPassword()), "Mật khẩu cũ không còn dùng được");
            long minutes = java.time.Duration.between(OffsetDateTime.now(), reloaded.getTemporaryPasswordExpiresAt()).toMinutes();
            assertTrue(minutes >= 9 && minutes <= 10, "Hạn 10 phút, thực tế còn " + minutes);
            assertEquals(username, response.getUsername());
            assertTrue(response.getMaskedEmail().contains("***"));
        } finally {
            accountRepository.findById(username).ifPresent(accountRepository::delete);
        }
    }

    private void createAccount(String username, String passwordHash) {
        OffsetDateTime now = OffsetDateTime.now();
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordHash);
        account.setEmail(username + "@example.com");
        account.setAuthProvider("LOCAL");
        account.setMustChangePassword(Boolean.FALSE);
        account.setStatus(true);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy("super-admin");
        account.setUpdatedBy("super-admin");
        accountRepository.saveAndFlush(account);
    }

    private Authentication superAdmin() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "it-reset-superadmin", null, List.of("SUPER_ADMIN"),
                List.of(AccountManagementAuthorizationHelper.RESET_PASSWORD_ALL));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
