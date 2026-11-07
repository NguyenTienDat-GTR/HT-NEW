package phucuong.ht.modules.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Xác minh Cloudflare Turnstile (CAPTCHA miễn phí, không theo dõi người dùng) cho form đăng nhập.
 * Tắt hoàn toàn khi chưa cấu hình TURNSTILE_SECRET_KEY, nên triển khai code này không đổi hành vi cho tới khi bật.
 */
@Slf4j
@Service
public class TurnstileService {

    private static final URI VERIFY_URL = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");

    private final String siteKey;
    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TurnstileService(
            @Value("${app.security.turnstile.site-key:}") String siteKey,
            @Value("${app.security.turnstile.secret-key:}") String secretKey,
            ObjectMapper objectMapper
    ) {
        this.siteKey = siteKey == null ? "" : siteKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return !siteKey.isEmpty() && !secretKey.isEmpty();
    }

    public String getSiteKey() {
        return siteKey;
    }

    /** Ném lỗi 400 khi token thiếu/không hợp lệ; không làm gì khi Turnstile chưa bật. */
    public void assertHuman(String token, String clientIp) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw rejected();
        }
        try {
            StringBuilder body = new StringBuilder()
                    .append("secret=").append(encode(secretKey))
                    .append("&response=").append(encode(token));
            if (StringUtils.hasText(clientIp)) {
                body.append("&remoteip=").append(encode(clientIp));
            }
            HttpRequest request = HttpRequest.newBuilder(VERIFY_URL)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() == 200 && json.path("success").asBoolean(false)) {
                return;
            }
            log.warn("Turnstile từ chối token: {}", json.path("error-codes"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
        throw rejected();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ApiException rejected() {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                "Không xác minh được bạn là người thật, vui lòng thử lại");
    }

    private static ApiException unavailable(Exception cause) {
        log.error("Không gọi được Cloudflare Turnstile", cause);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.VALIDATION_ERROR,
                "Không kiểm tra được xác minh bảo mật, vui lòng thử lại sau ít phút");
    }
}
