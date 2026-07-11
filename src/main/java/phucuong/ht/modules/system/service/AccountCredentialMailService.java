package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import phucuong.ht.common.validation.CommonValidationHelper;

@Service
@RequiredArgsConstructor
public class AccountCredentialMailService {

    private final JavaMailSender mailSender;
    private final CommonValidationHelper validation;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    public void sendInitialCredentials(String recipientEmail, String recipientName, String username, String rawPassword) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw validation.validationError("Email nhận thông tin tài khoản là bắt buộc");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) message.setFrom(from);
            message.setTo(recipientEmail);
            message.setSubject("Thông tin tài khoản hệ thống quản lí Huynh Trưởng");
            message.setText("Xin chào " + displayName(recipientName) + ",\n\n"
                    + "Tài khoản của bạn đã được tạo.\n"
                    + "Tên đăng nhập: " + username + "\n"
                    + "Mật khẩu tạm thời: " + rawPassword + "\n\n"
                    + "Vui lòng đăng nhập và đổi mật khẩu ngay. Không chia sẻ mật khẩu này với người khác.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw validation.validationError("Không thể gửi email thông tin tài khoản; tài khoản chưa được tạo");
        }
    }

    private String displayName(String recipientName) {
        return recipientName == null || recipientName.isBlank() ? "Quản trị viên" : recipientName.trim();
    }
}
