package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.EmailUpdateRequest;
import phucuong.ht.modules.auth.model.response.EmailUpdateResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Tài khoản không gắn Huynh Trưởng (ADMIN_DIOCESE, SUPER_ADMIN, vai trò toàn hệ thống) tự đổi email nhận thông tin.
 * Email này nhận OTP đổi / quên mật khẩu nên bắt buộc nhập đúng mật khẩu hiện tại, sai nhiều lần sẽ bị khóa tạm.
 * Email có thể trùng với tài khoản khác.
 */
@Service
@RequiredArgsConstructor
public class MyEmailService {

    private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordOtpStore otpStore;

    @Transactional
    public EmailUpdateResponse update(EmailUpdateRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Yêu cầu xác thực");
        }
        String username = principal.getUsername();
        if (otpStore.oldPasswordFailures(username) >= PasswordChangeService.MAX_OLD_PASSWORD_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Bạn đã nhập sai mật khẩu hiện tại quá nhiều lần, vui lòng thử lại sau 15 phút");
        }
        Account account = accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));
        if (account.getLeader() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Tài khoản gắn Huynh Trưởng dùng email trong hồ sơ Huynh Trưởng, không đổi ở đây");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            otpStore.recordOldPasswordFailure(username, FAIL_WINDOW);
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu hiện tại không chính xác");
        }
        otpStore.clearOldPasswordFailures(username);

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        account.setEmail(email);
        account.setUpdatedAt(OffsetDateTime.now());
        account.setUpdatedBy(username);
        accountRepository.saveAndFlush(account);
        return EmailUpdateResponse.builder().email(email).build();
    }
}
