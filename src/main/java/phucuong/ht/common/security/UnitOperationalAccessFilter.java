package phucuong.ht.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class UnitOperationalAccessFilter extends OncePerRequestFilter {

    private final UnitOperationalStatusService unitOperationalStatusService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!unitOperationalStatusService.isAccountUnitLocked(principal.getUsername()) || isAllowedWhileLocked(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                apiResponse.error("Tai khoan thuoc don vi dang bi khoa, chi duoc dang nhap va doc thong bao", errorCode.ACCESS_DENIED.name())
        );
    }

    private boolean isAllowedWhileLocked(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/ws/notifications")) {
            return true;
        }
        if (path.equals("/api/auth/me") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.equals("/api/auth/refresh") || path.equals("/api/auth/logout")) {
            return true;
        }
        if (path.equals("/api/system/notifications") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.equals("/api/system/notifications/unread-count") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        return path.startsWith("/api/system/notifications/") && path.endsWith("/read") && "PATCH".equalsIgnoreCase(method);
    }
}
