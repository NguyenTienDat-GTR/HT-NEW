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

    public void writeRefreshCookie(HttpServletResponse response, AuthTokenResponse tokens) {
        response.addHeader("Set-Cookie", buildCookie(
                authProperties.getCookie().getRefreshTokenName(),
                tokens.getRefreshToken(),
                tokens.getRefreshTokenExpiresInMs()
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

    private ResponseCookie buildCookie(String name, String value, long maxAgeMs) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path(authProperties.getCookie().getPath())
                .maxAge(maxAgeMs / 1000)
                .build();
    }
}
