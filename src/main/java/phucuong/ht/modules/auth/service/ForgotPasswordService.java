package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.request.ForgotPasswordOtpRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordResetRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordVerifyRequest;
import phucuong.ht.modules.auth.model.response.ForgotPasswordOtpResponse;
import phucuong.ht.modules.auth.model.response.ForgotPasswordVerifyResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Quên mật khẩu (không cần đăng nhập) gồm 3 bước:
 * 1. Nhập tên đăng nhập: gửi OTP 6 số tới email Huynh Trưởng (hoặc email tài khoản), trả về email đã che.
 * 2. Xác thực OTP: cấp reset token dùng một lần.
 * 3. Đặt mật khẩu mới bằng reset token: thu hồi mọi phiên đăng nhập và gửi email thông báo.
 * Không đổi gì trên tài khoản cho tới khi có OTP đúng; mọi lỗi tìm tài khoản dùng chung một thông báo để không dò được tài khoản.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(10);
    static final Duration SEND_WINDOW = Duration.ofHours(1);
    static final int MAX_SENDS_PER_WINDOW = 5;
    static final int MAX_OTP_ATTEMPTS = 5;

    static final String UNAVAILABLE_MESSAGE =
            "Không thể khôi phục mật khẩu cho tài khoản này, vui lòng liên hệ quản trị viên";

    /** Tiền tố để OTP / token của luồng này không đè lên luồng đổi mật khẩu khi đang đăng nhập. */
    private static final String SUBJECT_PREFIX = "forgot:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordOtpStore otpStore;
    private final PasswordOtpMailService mailService;
    private final AccountEmailResolver emailResolver;
    private final RedisTokenSessionService redisTokenSessionService;

    public ForgotPasswordOtpResponse sendOtp(ForgotPasswordOtpRequest request) {
        String username = normalize(request.getUsername());
        Account account = accountRepository.findActiveByUsername(username).orElse(null);
        String email = account == null ? null : emailResolver.resolve(account);
        if (email == null) {
            throw unavailable();
        }

        String subject = subject(username);
        if (!otpStore.tryStartCooldown(subject, RESEND_COOLDOWN)) {
            long wait = otpStore.cooldownRemainingSeconds(subject);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Vui lòng đợi " + Math.max(wait, 1) + " giây trước khi gửi lại mã OTP");
        }
        if (otpStore.recordSend(subject, SEND_WINDOW) > MAX_SENDS_PER_WINDOW) {
            otpStore.undoSend(subject);
            otpStore.clearCooldown(subject);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Bạn đã yêu cầu mã OTP quá nhiều lần, vui lòng thử lại sau 1 giờ");
        }

        // "Tất cả hoặc không": gửi email lỗi thì khôi phục OTP cũ, bỏ thời gian chờ, hoàn lại lượt gửi và báo lỗi.
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordOtpStore.OtpSnapshot previousOtp = null;
        boolean snapshotTaken = false;
        try {
            previousOtp = otpStore.snapshotOtp(subject);
            snapshotTaken = true;
            otpStore.saveOtp(subject, otp, OTP_TTL);
            mailService.sendForgotOtp(email, username, otp, OTP_TTL.toMinutes());
            // Mã mới đã tới email: phiên khôi phục cũ (nếu có) không còn hiệu lực.
            otpStore.deleteChangeToken(subject);
        } catch (RuntimeException exception) {
            if (snapshotTaken) {
                PasswordOtpStore.OtpSnapshot snapshot = previousOtp;
                compensate("khôi phục OTP cũ", () -> otpStore.restoreOtp(subject, snapshot));
            }
            compensate("bỏ thời gian chờ gửi lại", () -> otpStore.clearCooldown(subject));
            compensate("hoàn lại lượt gửi OTP", () -> otpStore.undoSend(subject));
            throw asApiException(exception, "Không gửi được mã OTP, vui lòng thử lại sau");
        }

        return ForgotPasswordOtpResponse.builder()
                .maskedEmail(EmailMasker.mask(email))
                .expiresInSeconds(OTP_TTL.toSeconds())
                .resendAfterSeconds(RESEND_COOLDOWN.toSeconds())
                .build();
    }

    public ForgotPasswordVerifyResponse verifyOtp(ForgotPasswordVerifyRequest request) {
        String username = normalize(request.getUsername());
        String subject = subject(username);
        PasswordOtpStore.StoredOtp stored = otpStore.findOtp(subject);
        if (stored == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Mã OTP đã hết hạn hoặc chưa được gửi, vui lòng gửi lại mã mới");
        }
        if (stored.attempts() >= MAX_OTP_ATTEMPTS) {
            otpStore.deleteOtp(subject);
            throw tooManyOtpAttempts();
        }
        if (!otpStore.matchesOtp(subject, request.getOtp(), stored)) {
            long attempts = otpStore.incrementOtpAttempts(subject);
            long remaining = Math.max(0, MAX_OTP_ATTEMPTS - attempts);
            if (remaining == 0) {
                otpStore.deleteOtp(subject);
                throw tooManyOtpAttempts();
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Mã OTP không chính xác, bạn còn " + remaining + " lần thử");
        }

        // Cấp reset token trước rồi mới hủy OTP; lỗi giữa chừng thì gỡ token để OTP vẫn dùng lại được.
        String resetToken = newResetToken();
        try {
            otpStore.saveChangeToken(subject, resetToken, RESET_TOKEN_TTL);
            otpStore.deleteOtp(subject);
        } catch (RuntimeException exception) {
            compensate("gỡ phiên khôi phục mật khẩu", () -> otpStore.deleteChangeToken(subject));
            throw asApiException(exception, "Không xác thực được OTP lúc này, vui lòng thử lại");
        }
        return ForgotPasswordVerifyResponse.builder()
                .resetToken(resetToken)
                .expiresInSeconds(RESET_TOKEN_TTL.toSeconds())
                .build();
    }

    /**
     * Ghi mật khẩu mới (flush để lỗi DB lộ ra trước), hủy reset token rồi thu hồi mọi phiên, tất cả trong một transaction.
     * Bước nào lỗi thì ném lỗi nên DB rollback, reset token được trả lại để người dùng thử lại mà không cần OTP mới.
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ForgotPasswordResetRequest request) {
        String username = normalize(request.getUsername());
        String subject = subject(username);
        if (!otpStore.matchesChangeToken(subject, request.getResetToken())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Phiên khôi phục mật khẩu đã hết hạn, vui lòng xác thực OTP lại");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu xác nhận không khớp");
        }

        Account account = accountRepository.findActiveByUsername(username).orElseThrow(this::unavailable);
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
            otpStore.deleteChangeToken(subject);
            tokenConsumed = true;
            redisTokenSessionService.revokeAllSessions(username);
        } catch (RuntimeException exception) {
            if (tokenConsumed) {
                compensate("trả lại phiên khôi phục mật khẩu",
                        () -> otpStore.saveChangeToken(subject, request.getResetToken(), RESET_TOKEN_TTL));
            }
            throw asApiException(exception, "Không đặt lại được mật khẩu, vui lòng thử lại");
        }

        String email = emailResolver.resolve(account);
        if (email != null) {
            notifyAfterCommit(email, username);
        }
    }

    /** Email thông báo chỉ gửi sau khi transaction đã commit; lỗi gửi chỉ ghi log vì mật khẩu đã đổi xong. */
    private void notifyAfterCommit(String email, String username) {
        Runnable notice = () -> {
            try {
                mailService.sendPasswordChangedNotice(email, username);
            } catch (RuntimeException exception) {
                log.warn("Không gửi được email thông báo đổi mật khẩu cho tài khoản {}", username, exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notice.run();
                }
            });
        } else {
            notice.run();
        }
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim();
    }

    private String subject(String username) {
        return SUBJECT_PREFIX + username;
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, UNAVAILABLE_MESSAGE);
    }

    private ApiException tooManyOtpAttempts() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                "Bạn đã nhập sai OTP quá nhiều lần, vui lòng gửi lại mã mới");
    }

    /** Bước bù trừ khi rollback: lỗi ở đây chỉ ghi log để không che lỗi gốc. */
    private void compensate(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error("Rollback quên mật khẩu thất bại ở bước {}", step, exception);
        }
    }

    private ApiException asApiException(RuntimeException exception, String message) {
        if (exception instanceof ApiException apiException) {
            return apiException;
        }
        log.error(message, exception);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR, message);
    }

    private String newResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
