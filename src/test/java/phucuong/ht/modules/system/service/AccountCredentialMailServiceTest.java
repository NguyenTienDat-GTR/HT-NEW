package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.common.validation.CommonValidationHelper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountCredentialMailServiceTest {

    @Test
    void emailShouldContainGeneratedUsernameAndPassword() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AccountCredentialMailService service = new AccountCredentialMailService(
                mailSender,
                mock(CommonValidationHelper.class)
        );
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");

        service.sendInitialCredentials("leader@example.com", "Nguyễn An", "anchanhtoa107", "004321");

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();
        assertTrue(body.contains("Tên đăng nhập: anchanhtoa107"));
        assertTrue(body.contains("Mật khẩu tạm thời: 004321"));
    }
}
