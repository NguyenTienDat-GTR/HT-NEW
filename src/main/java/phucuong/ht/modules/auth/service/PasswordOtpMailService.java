package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordOtpMailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    public void sendForgotOtp(String recipientEmail, String username, String otp, long expiresInMinutes) {
        try {
            SimpleMailMessage message = newMessage(recipientEmail, "Mã xác thực quên mật khẩu - Hệ thống quản lí Huynh Trưởng");
            message.setText("Xin chào " + username + ",\n\n"
                    + "Mã OTP để đặt lại mật khẩu của bạn là: " + otp + "\n"
                    + "Mã có hiệu lực trong " + expiresInMinutes + " phút và chỉ dùng được một lần.\n\n"
                    + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này; mật khẩu hiện tại vẫn được giữ nguyên.\n"
                    + "Tuyệt đối không chia sẻ mã này với bất kỳ ai.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.warn("Không thể gửi OTP quên mật khẩu cho tài khoản {}", username, exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR,
                    "Không thể gửi email OTP lúc này, vui lòng thử lại sau");
        }
    }

    /** Thông báo mật khẩu vừa được đặt lại; tuyệt đối không kèm mật khẩu. */
    public void sendPasswordChangedNotice(String recipientEmail, String username) {
        try {
            SimpleMailMessage message = newMessage(recipientEmail, "Mật khẩu đã được thay đổi - Hệ thống quản lí Huynh Trưởng");
            message.setText("Xin chào " + username + ",\n\n"
                    + "Mật khẩu của tài khoản " + username + " vừa được đặt lại thành công qua chức năng Quên mật khẩu.\n"
                    + "Mọi phiên đăng nhập cũ đã bị đăng xuất.\n\n"
                    + "Nếu không phải bạn thực hiện, hãy liên hệ quản trị viên ngay để được hỗ trợ.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.warn("Không thể gửi thông báo đổi mật khẩu cho tài khoản {}", username, exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR,
                    "Không thể gửi email thông báo lúc này");
        }
    }

    private SimpleMailMessage newMessage(String recipientEmail, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        return message;
    }

    public void sendOtp(String recipientEmail, String username, String otp, long expiresInMinutes) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) message.setFrom(from);
            message.setTo(recipientEmail);
            message.setSubject("Mã xác thực đổi mật khẩu - Hệ thống quản lí Huynh Trưởng");
            message.setText("Xin chào " + username + ",\n\n"
                    + "Mã OTP để đổi mật khẩu của bạn là: " + otp + "\n"
                    + "Mã có hiệu lực trong " + expiresInMinutes + " phút và chỉ dùng được một lần.\n\n"
                    + "Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này và kiểm tra lại bảo mật tài khoản.\n"
                    + "Tuyệt đối không chia sẻ mã này với bất kỳ ai.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.warn("Không thể gửi OTP đổi mật khẩu cho tài khoản {}", username, exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.INTERNAL_ERROR,
                    "Không thể gửi email OTP lúc này, vui lòng thử lại sau");
        }
    }
}
