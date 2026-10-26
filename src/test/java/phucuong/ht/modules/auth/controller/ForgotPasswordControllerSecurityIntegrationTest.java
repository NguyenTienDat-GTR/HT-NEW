package phucuong.ht.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.request.ForgotPasswordOtpRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordResetRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordVerifyRequest;
import phucuong.ht.modules.auth.model.response.ForgotPasswordOtpResponse;
import phucuong.ht.modules.auth.model.response.ForgotPasswordVerifyResponse;
import phucuong.ht.modules.auth.service.ForgotPasswordService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Các endpoint quên mật khẩu phải dùng được khi chưa đăng nhập (kể cả khi trình duyệt còn token cũ hỏng). */
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class ForgotPasswordControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ForgotPasswordService forgotPasswordService;

    @Test
    void sendOtpShouldBePublicAndReturnOnlyMaskedEmail() throws Exception {
        when(forgotPasswordService.sendOtp(any(ForgotPasswordOtpRequest.class))).thenReturn(
                ForgotPasswordOtpResponse.builder().maskedEmail("ng***@g***.com").expiresInSeconds(300).resendAfterSeconds(60).build());

        mockMvc.perform(post("/api/auth/forgot-password/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest("gx.phuoc.hoa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedEmail").value("ng***@g***.com"))
                .andExpect(jsonPath("$.message").value("Đã gửi mã OTP tới ng***@g***.com"));
    }

    @Test
    void sendOtpShouldIgnoreStaleBearerTokenInsteadOfAnsweringUnauthorized() throws Exception {
        when(forgotPasswordService.sendOtp(any(ForgotPasswordOtpRequest.class))).thenReturn(
                ForgotPasswordOtpResponse.builder().maskedEmail("ng***@g***.com").expiresInSeconds(300).resendAfterSeconds(60).build());

        mockMvc.perform(post("/api/auth/forgot-password/otp")
                        .header("Authorization", "Bearer expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest("gx.phuoc.hoa"))))
                .andExpect(status().isOk());
    }

    @Test
    void sendOtpShouldRejectBlankUsernameBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest("  "))))
                .andExpect(status().isBadRequest());

        verify(forgotPasswordService, never()).sendOtp(any());
    }

    @Test
    void sendOtpShouldSurfaceServiceFailureWithoutRevealingWhetherAccountExists() throws Exception {
        when(forgotPasswordService.sendOtp(any(ForgotPasswordOtpRequest.class))).thenThrow(
                new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, ForgotPasswordGenericMessage.TEXT));

        mockMvc.perform(post("/api/auth/forgot-password/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest("ghost"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ForgotPasswordGenericMessage.TEXT));
    }

    @Test
    void verifyOtpShouldBePublicAndRejectMalformedOtp() throws Exception {
        when(forgotPasswordService.verifyOtp(any(ForgotPasswordVerifyRequest.class))).thenReturn(
                ForgotPasswordVerifyResponse.builder().resetToken("token").expiresInSeconds(600).build());

        mockMvc.perform(post("/api/auth/forgot-password/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resetToken").value("token"));

        mockMvc.perform(post("/api/auth/forgot-password/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest("12ab"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetShouldBePublicAndEnforcePasswordLength() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest("NewPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest("short"))))
                .andExpect(status().isBadRequest());
    }

    private ForgotPasswordOtpRequest otpRequest(String username) {
        ForgotPasswordOtpRequest request = new ForgotPasswordOtpRequest();
        request.setUsername(username);
        return request;
    }

    private ForgotPasswordVerifyRequest verifyRequest(String otp) {
        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setUsername("gx.phuoc.hoa");
        request.setOtp(otp);
        return request;
    }

    private ForgotPasswordResetRequest resetRequest(String password) {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setUsername("gx.phuoc.hoa");
        request.setResetToken("token");
        request.setNewPassword(password);
        request.setConfirmPassword(password);
        return request;
    }

    private static final class ForgotPasswordGenericMessage {
        static final String TEXT = "Không thể khôi phục mật khẩu cho tài khoản này, vui lòng liên hệ quản trị viên";
    }
}
