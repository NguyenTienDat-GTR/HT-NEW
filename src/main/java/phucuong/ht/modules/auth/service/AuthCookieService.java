package phucuong.ht.modules.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;

@Service
public class AuthCookieService {

    private final AuthProperties authProperties;

    public AuthCookieService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    // Cookie access token (httpOnly, sống đúng bằng hạn token) để server FE render sẵn dữ liệu khi tải trang
    // mà không cần refresh (refresh sẽ xoay vòng refresh token). Bộ lọc JWT đã chấp nhận cookie này.
    public void writeAccessCookie(HttpServletResponse response, AuthTokenResponse tokens) {
        response.addHeader("Set-Cookie", buildCookie(
                authProperties.getCookie().getAccessTokenName(),
                tokens.getAccessToken(),
                tokens.getAccessTokenExpiresInMs()
        ).toString());
    }

    /** Bản trả về trong body: ẩn accessToken khi cấu hình không cho lộ (test/production), token vẫn nằm trong cookie HttpOnly. */
    public AuthTokenResponse toBodyResponse(AuthTokenResponse tokens) {
        if (authProperties.getCookie().isExposeAccessTokenInBody()) {
            return tokens;
        }
        return tokens.toBuilder().accessToken(null).build();
    }

    public void clearAccessCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(
                authProperties.getCookie().getAccessTokenName(),
                "",
                0L
        ).toString());
    }

    public void writeRefreshCookie(HttpServletResponse response, AuthTokenResponse tokens) {
        // Không ghi nhớ đăng nhập: cookie phiên (không Max-Age) nên đóng trình duyệt là mất.
        Long maxAgeMs = tokens.isRememberMe() ? Long.valueOf(tokens.getRefreshTokenExpiresInMs()) : null;
        response.addHeader("Set-Cookie", buildCookie(
                authProperties.getCookie().getRefreshTokenName(),
                tokens.getRefreshToken(),
                maxAgeMs
        ).toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(
                authProperties.getCookie().getRefreshTokenName(),
                "",
                0L
        ).toString());
    }

    public String resolveAccessToken(HttpServletRequest request) {
        return readCookie(request, authProperties.getCookie().getAccessTokenName());
    }

    public String resolveRefreshToken(HttpServletRequest request) {
        return readCookie(request, authProperties.getCookie().getRefreshTokenName());
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String name, String value, Long maxAgeMs) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path(authProperties.getCookie().getPath());
        if (maxAgeMs != null) {
            builder.maxAge(maxAgeMs / 1000);
        }
        return builder.build();
    }
}
