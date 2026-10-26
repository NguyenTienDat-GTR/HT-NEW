package phucuong.ht.modules.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.service.AuthCookieService;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final jwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final AuthCookieService authCookieService;
    private final RedisTokenSessionService redisTokenSessionService;
    @Value("${app.security.swagger-public-enabled:false}")
    private boolean swaggerPublicEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
        } else {
            token = authCookieService.resolveAccessToken(request);
        }

        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            jwtUtils.TokenClaims accessClaims = jwtUtils.parseAccessTokenClaims(token);
            if (!redisTokenSessionService.isAccessTokenActive(
                    accessClaims.sessionId(),
                    accessClaims.principal().getUsername(),
                    accessClaims.tokenId()
            )) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.INVALID_TOKEN, "Phiên đăng nhập không còn hiệu lực");
            }
            if (accessClaims.principal().isMustChangePassword() && !isTemporaryPasswordAllowedPath(request.getRequestURI())) {
                throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED,
                        "Bạn phải đổi mật khẩu tạm trước khi sử dụng chức năng nghiệp vụ");
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    accessClaims.principal(),
                    null,
                    accessClaims.principal().getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            writeAuthenticationResponse(response, exception.getStatus(), exception.getMessage(), exception.getErrorCode());
        }
    }

    private void writeAuthenticationResponse(HttpServletResponse response, HttpStatus status, String message, errorCode code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), apiResponse.error(message, code.name()));
    }

    private boolean isTemporaryPasswordAllowedPath(String path) {
        return "/api/auth/change-password".equals(path)
                || "/api/auth/me".equals(path)
                || path.startsWith("/api/system/notifications")
                || path.startsWith("/ws/notifications");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (swaggerPublicEnabled && (path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.equals("/swagger-auth-token.js")
                || path.startsWith("/v3/api-docs"))) {
            return true;
        }

        return path.startsWith("/actuator")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/api/auth/logout")
                || path.startsWith("/api/auth/forgot-password")
                || path.startsWith("/api/auth/bootstrap");
    }
}
