package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.PasswordChangeConfirmRequest;
import phucuong.ht.modules.auth.model.request.PasswordOtpSendRequest;
import phucuong.ht.modules.auth.model.request.PasswordOtpVerifyRequest;
import phucuong.ht.modules.auth.model.response.PasswordOtpSendResponse;
import phucuong.ht.modules.auth.model.response.PasswordOtpVerifyResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    private static final String USERNAME = "admin.diocese";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordOtpStore otpStore;
    @Mock
    private PasswordOtpMailService mailService;
    @Mock
    private RedisTokenSessionService redisTokenSessionService;

    private PasswordChangeService service;
    private Authentication authentication;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new PasswordChangeService(accountRepository, passwordEncoder, otpStore, mailService,
                new AccountEmailResolver(), redisTokenSessionService);
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(USERNAME, null, List.of("ADMIN_DIOCESE"), List.of(), false);
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        account = new Account();
        account.setUsername(USERNAME);
        account.setPassword("hashed-old");
        account.setEmail("adminthanhpho@gmail.com");
    }

    @Test
    void sendOtpShouldRejectWrongOldPasswordAndCountFailure() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(sendRequest("wrong"), authentication));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(otpStore).recordOldPasswordFailure(eq(USERNAME), any());
        verify(mailService, never()).sendOtp(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void sendOtpShouldMailSixDigitCodeToAccountEmail() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-pass", "hashed-old")).thenReturn(true);
        when(otpStore.tryStartCooldown(eq(USERNAME), any())).thenReturn(true);

        PasswordOtpSendResponse response = service.sendOtp(sendRequest("old-pass"), authentication);

        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendOtp(eq("adminthanhpho@gmail.com"), eq(USERNAME), otp.capture(), eq(5L));
        assertTrue(otp.getValue().matches("[0-9]{6}"));
        verify(otpStore).saveOtp(eq(USERNAME), eq(otp.getValue()), any());
        assertEquals(300, response.getExpiresInSeconds());
        assertEquals("ad***@g***.com", response.getEmail());
    }

    @Test
    void sendOtpShouldFailWhenAccountHasNoEmail() {
        account.setEmail(null);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-pass", "hashed-old")).thenReturn(true);

        assertThrows(ApiException.class, () -> service.sendOtp(sendRequest("old-pass"), authentication));
        verify(mailService, never()).sendOtp(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void verifyOtpShouldIssueChangeTokenWhenCodeMatches() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 0);
        when(otpStore.findOtp(USERNAME)).thenReturn(stored);
        when(otpStore.matchesOtp(USERNAME, "123456", stored)).thenReturn(true);

        PasswordOtpVerifyResponse response = service.verifyOtp(verifyRequest("123456"), authentication);

        assertNotNull(response.getChangeToken());
        verify(otpStore).deleteOtp(USERNAME);
        verify(otpStore).saveChangeToken(eq(USERNAME), eq(response.getChangeToken()), any());
    }

    @Test
    void verifyOtpShouldReportRemainingAttemptsOnWrongCode() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 1);
        when(otpStore.findOtp(USERNAME)).thenReturn(stored);
        when(otpStore.matchesOtp(USERNAME, "000000", stored)).thenReturn(false);
        when(otpStore.incrementOtpAttempts(USERNAME)).thenReturn(2L);

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("000000"), authentication));

        assertTrue(exception.getMessage().contains("3 lần"));
        verify(otpStore, never()).saveChangeToken(anyString(), anyString(), any());
    }

    @Test
    void changePasswordShouldKeepCurrentSessionAndRevokeOthers() {
        when(otpStore.matchesChangeToken(USERNAME, "token")).thenReturn(true);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");

        service.changePassword(changeRequest("token", "NewPass123", "NewPass123"), authentication, "session-1");

        assertEquals("hashed-new", account.getPassword());
        verify(accountRepository).saveAndFlush(account);
        verify(otpStore).deleteChangeToken(USERNAME);
        verify(redisTokenSessionService).revokeOtherSessions(USERNAME, "session-1");
    }

    @Test
    void changePasswordShouldRejectMismatchedConfirmation() {
        when(otpStore.matchesChangeToken(USERNAME, "token")).thenReturn(true);

        assertThrows(ApiException.class,
                () -> service.changePassword(changeRequest("token", "NewPass123", "Other123"), authentication, "session-1"));
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void changePasswordShouldRejectExpiredChangeToken() {
        when(otpStore.matchesChangeToken(USERNAME, "expired")).thenReturn(false);

        assertThrows(ApiException.class,
                () -> service.changePassword(changeRequest("expired", "NewPass123", "NewPass123"), authentication, "session-1"));
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendOtpMailFailureShouldRestorePreviousOtpAndCooldown() {
        PasswordOtpStore.OtpSnapshot previous =
                new PasswordOtpStore.OtpSnapshot(java.util.Map.of("hash", "old", "attempts", "0"), 120_000);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-pass", "hashed-old")).thenReturn(true);
        when(otpStore.tryStartCooldown(eq(USERNAME), any())).thenReturn(true);
        when(otpStore.snapshotOtp(USERNAME)).thenReturn(previous);
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        phucuong.ht.common.exception.errorCode.INTERNAL_ERROR, "SMTP down"))
                .when(mailService).sendOtp(anyString(), anyString(), anyString(), anyLong());

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(sendRequest("old-pass"), authentication));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(otpStore).restoreOtp(USERNAME, previous);
        verify(otpStore).clearCooldown(USERNAME);
        verify(otpStore, never()).deleteChangeToken(anyString());
    }

    @Test
    void sendOtpRedisFailureShouldBeReportedAsServiceUnavailable() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-pass", "hashed-old")).thenReturn(true);
        when(otpStore.tryStartCooldown(eq(USERNAME), any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(otpStore).saveOtp(anyString(), anyString(), any());

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(sendRequest("old-pass"), authentication));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(mailService, never()).sendOtp(anyString(), anyString(), anyString(), anyLong());
        verify(otpStore).clearCooldown(USERNAME);
    }

    @Test
    void verifyOtpShouldKeepOtpWhenChangeTokenCannotBeSaved() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 0);
        when(otpStore.findOtp(USERNAME)).thenReturn(stored);
        when(otpStore.matchesOtp(USERNAME, "123456", stored)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(otpStore).saveChangeToken(anyString(), anyString(), any());

        assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("123456"), authentication));

        verify(otpStore, never()).deleteOtp(USERNAME);
        verify(otpStore).deleteChangeToken(USERNAME);
    }

    @Test
    void changePasswordShouldRestoreChangeTokenWhenRevokingSessionsFails() {
        when(otpStore.matchesChangeToken(USERNAME, "token")).thenReturn(true);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(redisTokenSessionService).revokeOtherSessions(USERNAME, "session-1");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.changePassword(changeRequest("token", "NewPass123", "NewPass123"), authentication, "session-1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(otpStore).saveChangeToken(eq(USERNAME), eq("token"), any());
    }

    private PasswordOtpSendRequest sendRequest(String oldPassword) {
        PasswordOtpSendRequest request = new PasswordOtpSendRequest();
        request.setOldPassword(oldPassword);
        return request;
    }

    private PasswordOtpVerifyRequest verifyRequest(String otp) {
        PasswordOtpVerifyRequest request = new PasswordOtpVerifyRequest();
        request.setOtp(otp);
        return request;
    }

    private PasswordChangeConfirmRequest changeRequest(String token, String newPassword, String confirmPassword) {
        PasswordChangeConfirmRequest request = new PasswordChangeConfirmRequest();
        request.setChangeToken(token);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(confirmPassword);
        return request;
    }
}
