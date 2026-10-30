package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.PasswordChangeConfirmRequest;
import phucuong.ht.modules.auth.model.request.PasswordOtpSendRequest;
import phucuong.ht.modules.auth.model.request.PasswordOtpVerifyRequest;
import phucuong.ht.modules.auth.model.response.PasswordOtpSendResponse;
import phucuong.ht.modules.auth.model.response.PasswordOtpVerifyResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Đổi mật khẩu chủ động gồm 3 bước:
 * 1. Kiểm tra mật khẩu hiện tại rồi gửi OTP 6 số qua email.
 * 2. Xác thực OTP, cấp change token dùng một lần.
 * 3. Đặt mật khẩu mới bằng change token, giữ phiên hiện tại và thu hồi các phiên khác.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration CHANGE_TOKEN_TTL = Duration.ofMinutes(10);
    static final Duration OLD_PASSWORD_FAIL_WINDOW = Duration.ofMinutes(15);
    static final int MAX_OTP_ATTEMPTS = 5;
    static final int MAX_OLD_PASSWORD_FAILURES = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordOtpStore otpStore;
    private final PasswordOtpMailService mailService;
    private final AccountEmailResolver emailResolver;
    private final RedisTokenSessionService redisTokenSessionService;

    public PasswordOtpSendResponse sendOtp(PasswordOtpSendRequest request, Authentication authentication) {
        String username = requireUsername(authentication);
        if (otpStore.oldPasswordFailures(username) >= MAX_OLD_PASSWORD_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Bạn đã nhập sai mật khẩu hiện tại quá nhiều lần, vui lòng thử lại sau 15 phút");
        }

        Account account = requireAccount(username);
        if (!passwordEncoder.matches(request.getOldPassword(), account.getPassword())) {
            otpStore.recordOldPasswordFailure(username, OLD_PASSWORD_FAIL_WINDOW);
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu hiện tại không chính xác");
        }
        otpStore.clearOldPasswordFailures(username);

        String email = emailResolver.resolve(account);
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Tài khoản chưa có email nhận OTP, vui lòng liên hệ quản trị viên để cập nhật email");
        }

        if (!otpStore.tryStartCooldown(username, RESEND_COOLDOWN)) {
            long wait = otpStore.cooldownRemainingSeconds(username);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Vui lòng đợi " + Math.max(wait, 1) + " giây trước khi gửi lại mã OTP");
        }

        // Các bước sau là "tất cả hoặc không": gửi email lỗi thì khôi phục OTP cũ, bỏ thời gian chờ và báo lỗi.
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordOtpStore.OtpSnapshot previousOtp = null;
        boolean snapshotTaken = false;
        try {
            previousOtp = otpStore.snapshotOtp(username);
            snapshotTaken = true;
            otpStore.saveOtp(username, otp, OTP_TTL);
            mailService.sendOtp(email, username, otp, OTP_TTL.toMinutes());
            // Mã mới đã tới email: phiên đổi mật khẩu cũ (nếu có) không còn hiệu lực.
            otpStore.deleteChangeToken(username);
        } catch (RuntimeException exception) {
            if (snapshotTaken) {
                PasswordOtpStore.OtpSnapshot snapshot = previousOtp;
                compensate("khôi phục OTP cũ", () -> otpStore.restoreOtp(username, snapshot));
            }
            compensate("bỏ thời gian chờ gửi lại", () -> otpStore.clearCooldown(username));
            throw asApiException(exception, "Không gửi được mã OTP, vui lòng thử lại sau");
        }

        return PasswordOtpSendResponse.builder()
                .email(EmailMasker.mask(email))
                .expiresInSeconds(OTP_TTL.toSeconds())
                .resendAfterSeconds(RESEND_COOLDOWN.toSeconds())
                .build();
    }

    public PasswordOtpVerifyResponse verifyOtp(PasswordOtpVerifyRequest request, Authentication authentication) {
        String username = requireUsername(authentication);
        PasswordOtpStore.StoredOtp stored = otpStore.findOtp(username);
        if (stored == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Mã OTP đã hết hạn hoặc chưa được gửi, vui lòng gửi lại mã mới");
        }
        if (stored.attempts() >= MAX_OTP_ATTEMPTS) {
            otpStore.deleteOtp(username);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Bạn đã nhập sai OTP quá nhiều lần, vui lòng gửi lại mã mới");
        }
        if (!otpStore.matchesOtp(username, request.getOtp(), stored)) {
            long attempts = otpStore.incrementOtpAttempts(username);
            long remaining = Math.max(0, MAX_OTP_ATTEMPTS - attempts);
            if (remaining == 0) {
                otpStore.deleteOtp(username);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                        "Bạn đã nhập sai OTP quá nhiều lần, vui lòng gửi lại mã mới");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Mã OTP không chính xác, bạn còn " + remaining + " lần thử");
        }

        // Cấp change token trước rồi mới hủy OTP; lỗi giữa chừng thì gỡ token để OTP vẫn dùng lại được.
        String changeToken = newChangeToken();
        try {
            otpStore.saveChangeToken(username, changeToken, CHANGE_TOKEN_TTL);
            otpStore.deleteOtp(username);
        } catch (RuntimeException exception) {
            compensate("gỡ phiên đổi mật khẩu", () -> otpStore.deleteChangeToken(username));
            throw asApiException(exception, "Không xác thực được OTP lúc này, vui lòng thử lại");
        }
        return PasswordOtpVerifyResponse.builder()
                .changeToken(changeToken)
                .expiresInSeconds(CHANGE_TOKEN_TTL.toSeconds())
                .build();
    }

    /**
     * Ghi mật khẩu mới (flush để lỗi DB lộ ra trước), hủy change token rồi thu hồi các phiên khác, tất cả trong một transaction.
     * Bất kỳ bước nào lỗi: ném lỗi nên DB rollback (rollbackFor = Exception), change token được trả lại để người dùng thử lại mà không cần OTP mới, và báo lỗi.
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeConfirmRequest request, Authentication authentication, String currentSessionId) {
        String username = requireUsername(authentication);
        if (!otpStore.matchesChangeToken(username, request.getChangeToken())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Phiên đổi mật khẩu đã hết hạn, vui lòng xác thực OTP lại");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu xác nhận không khớp");
        }

        Account account = requireAccount(username);
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        account.setMustChangePassword(Boolean.FALSE);
        account.setTemporaryPasswordExpiresAt(null);
        account.setUpdatedAt(OffsetDateTime.now());
        account.setUpdatedBy(username);
        boolean tokenConsumed = false;
        try {
            accountRepository.saveAndFlush(account);
            otpStore.deleteChangeToken(username);
            tokenConsumed = true;
            redisTokenSessionService.revokeOtherSessions(username, currentSessionId);
        } catch (RuntimeException exception) {
            if (tokenConsumed) {
                compensate("trả lại phiên đổi mật khẩu",
                        () -> otpStore.saveChangeToken(username, request.getChangeToken(), CHANGE_TOKEN_TTL));
            }
            throw asApiException(exception, "Không đổi được mật khẩu, vui lòng thử lại");
        }
    }

    /** Bước bù trừ khi rollback: lỗi ở đây chỉ ghi log để không che lỗi gốc. */
    private void compensate(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error("Rollback đổi mật khẩu thất bại ở bước {}", step, exception);
        }
    }

    private ApiException asApiException(RuntimeException exception, String message) {
        if (exception instanceof ApiException apiException) {
            return apiException;
        }
        log.error(message, exception);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR, message);
    }

    private String requireUsername(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Yêu cầu xác thực");
        }
        return principal.getUsername();
    }

    private Account requireAccount(String username) {
        return accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));
    }

    private String newChangeToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
