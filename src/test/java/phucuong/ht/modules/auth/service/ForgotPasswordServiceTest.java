package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.request.ForgotPasswordOtpRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordResetRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordVerifyRequest;
import phucuong.ht.modules.auth.model.response.ForgotPasswordOtpResponse;
import phucuong.ht.modules.auth.model.response.ForgotPasswordVerifyResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.system.model.entity.Account;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordServiceTest {

    private static final String USERNAME = "gx.phuoc.hoa";
    private static final String SUBJECT = "forgot:" + USERNAME;

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

    private ForgotPasswordService service;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new ForgotPasswordService(accountRepository, passwordEncoder, otpStore, mailService,
                new AccountEmailResolver(), redisTokenSessionService);
        account = new Account();
        account.setUsername(USERNAME);
        account.setPassword("hashed-old");
        account.setEmail("account@gmail.com");
    }

    // ---- Gửi OTP

    @Test
    void sendOtpShouldMailLeaderEmailFirstAndReturnMaskedEmail() {
        Leader leader = new Leader();
        leader.setEmail("huynhtruong@yahoo.com");
        account.setLeader(leader);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(otpStore.tryStartCooldown(eq(SUBJECT), any())).thenReturn(true);
        when(otpStore.recordSend(eq(SUBJECT), any())).thenReturn(1L);

        ForgotPasswordOtpResponse response = service.sendOtp(otpRequest(" " + USERNAME + " "));

        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendForgotOtp(eq("huynhtruong@yahoo.com"), eq(USERNAME), otp.capture(), eq(5L));
        assertTrue(otp.getValue().matches("[0-9]{6}"));
        verify(otpStore).saveOtp(eq(SUBJECT), eq(otp.getValue()), any());
        verify(otpStore).deleteChangeToken(SUBJECT);
        assertEquals("hu***@y***.com", response.getMaskedEmail());
        assertEquals(300, response.getExpiresInSeconds());
        assertEquals(60, response.getResendAfterSeconds());
    }

    @Test
    void sendOtpShouldFallBackToAccountEmailWhenNoLeaderEmail() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(otpStore.tryStartCooldown(eq(SUBJECT), any())).thenReturn(true);
        when(otpStore.recordSend(eq(SUBJECT), any())).thenReturn(1L);

        ForgotPasswordOtpResponse response = service.sendOtp(otpRequest(USERNAME));

        verify(mailService).sendForgotOtp(eq("account@gmail.com"), eq(USERNAME), anyString(), anyLong());
        assertEquals("ac***@g***.com", response.getMaskedEmail());
    }

    @Test
    void sendOtpShouldGiveTheSameGenericErrorForUnknownAccountAndMissingEmail() {
        when(accountRepository.findActiveByUsername("ghost")).thenReturn(Optional.empty());
        account.setEmail(null);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));

        ApiException unknown = assertThrows(ApiException.class, () -> service.sendOtp(otpRequest("ghost")));
        ApiException noEmail = assertThrows(ApiException.class, () -> service.sendOtp(otpRequest(USERNAME)));

        assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatus());
        assertEquals(unknown.getMessage(), noEmail.getMessage());
        assertEquals(unknown.getStatus(), noEmail.getStatus());
        verify(mailService, never()).sendForgotOtp(anyString(), anyString(), anyString(), anyLong());
        verify(otpStore, never()).saveOtp(anyString(), anyString(), any());
    }

    @Test
    void sendOtpShouldRejectDuringResendCooldown() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(otpStore.tryStartCooldown(eq(SUBJECT), any())).thenReturn(false);
        when(otpStore.cooldownRemainingSeconds(SUBJECT)).thenReturn(42L);

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(otpRequest(USERNAME)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertTrue(exception.getMessage().contains("42"));
        verify(mailService, never()).sendForgotOtp(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void sendOtpShouldLimitNumberOfCodesPerHourAndReleaseCooldown() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(otpStore.tryStartCooldown(eq(SUBJECT), any())).thenReturn(true);
        when(otpStore.recordSend(eq(SUBJECT), any())).thenReturn(6L);

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(otpRequest(USERNAME)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        verify(otpStore).undoSend(SUBJECT);
        verify(otpStore).clearCooldown(SUBJECT);
        verify(mailService, never()).sendForgotOtp(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void sendOtpShouldRollbackEverythingWhenMailFails() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(otpStore.tryStartCooldown(eq(SUBJECT), any())).thenReturn(true);
        when(otpStore.recordSend(eq(SUBJECT), any())).thenReturn(1L);
        PasswordOtpStore.OtpSnapshot previous = new PasswordOtpStore.OtpSnapshot(Map.of("hash", "old"), 120_000L);
        when(otpStore.snapshotOtp(SUBJECT)).thenReturn(previous);
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR, "SMTP down"))
                .when(mailService).sendForgotOtp(anyString(), anyString(), anyString(), anyLong());

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(otpRequest(USERNAME)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(otpStore).restoreOtp(SUBJECT, previous);
        verify(otpStore).clearCooldown(SUBJECT);
        verify(otpStore).undoSend(SUBJECT);
        verify(otpStore, never()).deleteChangeToken(SUBJECT);
    }

    @Test
    void sendOtpShouldStillRollbackWhenOneCompensationStepFails() {
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(otpStore.tryStartCooldown(eq(SUBJECT), any())).thenReturn(true);
        when(otpStore.recordSend(eq(SUBJECT), any())).thenReturn(1L);
        doThrow(new IllegalStateException("redis down")).when(mailService)
                .sendForgotOtp(anyString(), anyString(), anyString(), anyLong());
        doThrow(new IllegalStateException("redis down")).when(otpStore).restoreOtp(eq(SUBJECT), any());

        ApiException exception = assertThrows(ApiException.class, () -> service.sendOtp(otpRequest(USERNAME)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(otpStore).clearCooldown(SUBJECT);
        verify(otpStore).undoSend(SUBJECT);
    }

    // ---- Xác thực OTP

    @Test
    void verifyOtpShouldIssueResetTokenAndConsumeOtp() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 0);
        when(otpStore.findOtp(SUBJECT)).thenReturn(stored);
        when(otpStore.matchesOtp(SUBJECT, "123456", stored)).thenReturn(true);

        ForgotPasswordVerifyResponse response = service.verifyOtp(verifyRequest("123456"));

        assertNotNull(response.getResetToken());
        assertTrue(response.getResetToken().length() >= 40);
        assertEquals(600, response.getExpiresInSeconds());
        InOrder order = inOrder(otpStore);
        order.verify(otpStore).saveChangeToken(eq(SUBJECT), eq(response.getResetToken()), any());
        order.verify(otpStore).deleteOtp(SUBJECT);
    }

    @Test
    void verifyOtpShouldRejectExpiredOrMissingOtp() {
        when(otpStore.findOtp(SUBJECT)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("123456")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(otpStore, never()).saveChangeToken(anyString(), anyString(), any());
    }

    @Test
    void verifyOtpShouldCountWrongAttemptsAndTellRemainingTries() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 1);
        when(otpStore.findOtp(SUBJECT)).thenReturn(stored);
        when(otpStore.matchesOtp(SUBJECT, "000000", stored)).thenReturn(false);
        when(otpStore.incrementOtpAttempts(SUBJECT)).thenReturn(2L);

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("000000")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("3"));
        verify(otpStore, never()).deleteOtp(SUBJECT);
    }

    @Test
    void verifyOtpShouldDestroyOtpAfterFifthWrongAttempt() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 4);
        when(otpStore.findOtp(SUBJECT)).thenReturn(stored);
        when(otpStore.matchesOtp(SUBJECT, "000000", stored)).thenReturn(false);
        when(otpStore.incrementOtpAttempts(SUBJECT)).thenReturn(5L);

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("000000")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        verify(otpStore).deleteOtp(SUBJECT);
    }

    @Test
    void verifyOtpShouldRejectEvenCorrectCodeOnceAttemptsAreExhausted() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 5);
        when(otpStore.findOtp(SUBJECT)).thenReturn(stored);

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("123456")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        verify(otpStore).deleteOtp(SUBJECT);
        verify(otpStore, never()).saveChangeToken(anyString(), anyString(), any());
    }

    @Test
    void verifyOtpShouldKeepOtpUsableWhenIssuingTokenFails() {
        PasswordOtpStore.StoredOtp stored = new PasswordOtpStore.StoredOtp("hash", 0);
        when(otpStore.findOtp(SUBJECT)).thenReturn(stored);
        when(otpStore.matchesOtp(SUBJECT, "123456", stored)).thenReturn(true);
        doThrow(new IllegalStateException("redis down")).when(otpStore).saveChangeToken(eq(SUBJECT), anyString(), any());

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyOtp(verifyRequest("123456")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(otpStore).deleteChangeToken(SUBJECT);
        verify(otpStore, never()).deleteOtp(SUBJECT);
    }

    // ---- Đặt lại mật khẩu

    @Test
    void resetPasswordShouldWriteNewPasswordRevokeAllSessionsAndNotify() {
        account.setMustChangePassword(Boolean.TRUE);
        account.setTemporaryPasswordExpiresAt(OffsetDateTime.now().minusMinutes(30));
        when(otpStore.matchesChangeToken(SUBJECT, "token")).thenReturn(true);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");

        service.resetPassword(resetRequest("token", "NewPass123", "NewPass123"));

        assertEquals("hashed-new", account.getPassword());
        assertFalse(account.getMustChangePassword());
        assertNull(account.getTemporaryPasswordExpiresAt());
        assertEquals(USERNAME, account.getUpdatedBy());
        InOrder order = inOrder(accountRepository, otpStore, redisTokenSessionService);
        order.verify(accountRepository).saveAndFlush(account);
        order.verify(otpStore).deleteChangeToken(SUBJECT);
        order.verify(redisTokenSessionService).revokeAllSessions(USERNAME);
        verify(mailService).sendPasswordChangedNotice("account@gmail.com", USERNAME);
    }

    @Test
    void resetPasswordShouldRejectInvalidOrExpiredResetToken() {
        when(otpStore.matchesChangeToken(SUBJECT, "bad")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.resetPassword(resetRequest("bad", "NewPass123", "NewPass123")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
        verify(redisTokenSessionService, never()).revokeAllSessions(anyString());
    }

    @Test
    void resetPasswordShouldRejectMismatchedConfirmation() {
        when(otpStore.matchesChangeToken(SUBJECT, "token")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.resetPassword(resetRequest("token", "NewPass123", "Different1")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void resetPasswordShouldRejectReusingCurrentPassword() {
        when(otpStore.matchesChangeToken(SUBJECT, "token")).thenReturn(true);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("SamePass123", "hashed-old")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.resetPassword(resetRequest("token", "SamePass123", "SamePass123")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
        verify(otpStore, never()).deleteChangeToken(SUBJECT);
    }

    @Test
    void resetPasswordShouldGiveTokenBackWhenRevokingSessionsFails() {
        when(otpStore.matchesChangeToken(SUBJECT, "token")).thenReturn(true);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");
        doThrow(new IllegalStateException("redis down")).when(redisTokenSessionService).revokeAllSessions(USERNAME);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.resetPassword(resetRequest("token", "NewPass123", "NewPass123")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(otpStore).saveChangeToken(eq(SUBJECT), eq("token"), any());
        verify(mailService, never()).sendPasswordChangedNotice(anyString(), anyString());
    }

    @Test
    void resetPasswordShouldNotFailWhenNoticeEmailCannotBeSent() {
        when(otpStore.matchesChangeToken(SUBJECT, "token")).thenReturn(true);
        when(accountRepository.findActiveByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR, "SMTP down"))
                .when(mailService).sendPasswordChangedNotice(anyString(), anyString());

        service.resetPassword(resetRequest("token", "NewPass123", "NewPass123"));

        assertEquals("hashed-new", account.getPassword());
    }

    private ForgotPasswordOtpRequest otpRequest(String username) {
        ForgotPasswordOtpRequest request = new ForgotPasswordOtpRequest();
        request.setUsername(username);
        return request;
    }

    private ForgotPasswordVerifyRequest verifyRequest(String otp) {
        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setUsername(USERNAME);
        request.setOtp(otp);
        return request;
    }

    private ForgotPasswordResetRequest resetRequest(String token, String password, String confirm) {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setUsername(USERNAME);
        request.setResetToken(token);
        request.setNewPassword(password);
        request.setConfirmPassword(confirm);
        return request;
    }
}
