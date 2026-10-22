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
    void sendFailureShouldRaiseServiceUnavailableSoCreationRollsBack() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        AccountCredentialMailService service = new AccountCredentialMailService(mailSender, mock(CommonValidationHelper.class));

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                phucuong.ht.modules.auth.exception.ApiException.class,
                () -> service.sendInitialCredentials("leader@example.com", "Nguyễn An", "an", "004321"));

        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertTrue(exception.getMessage().contains("Tài khoản chưa được tạo"));
    }

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
