package phucuong.ht.config;

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
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimitService redisRateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !rateLimitProperties.isEnabled()
                || path == null
                || !path.startsWith("/api/")
                || path.startsWith("/api/swagger")
                || path.startsWith("/api-docs")
                || path.startsWith("/api/v3")
                || path.startsWith("/api/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String tier = resolveTier(request);
        RateLimitProperties.Tier tierConfig = resolveTierConfig(tier);
        String key = buildKey(request, tier);
        RedisRateLimitService.RateLimitDecision decision = redisRateLimitService.evaluate(
                key,
                tierConfig.getLimit(),
                tierConfig.getWindowSeconds()
        );

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            objectMapper.writeValue(
                    response.getWriter(),
                    apiResponse.error("Quá nhiều yêu cầu, vui lòng thử lại sau", errorCode.CONFLICT.name())
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitProperties.Tier resolveTierConfig(String tier) {
        return switch (tier) {
            case "auth" -> rateLimitProperties.getAuth();
            case "workflow" -> rateLimitProperties.getWorkflow();
            case "write" -> rateLimitProperties.getWrite();
            default -> rateLimitProperties.getRead();
        };
    }

    private String resolveTier(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/refresh")) {
            return "auth";
        }

        if (path.contains("/registrations")
                || path.contains("/approve")
                || path.contains("/approval")
                || path.contains("/score")
                || path.contains("/submit")) {
            return "workflow";
        }

        if ("GET".equalsIgnoreCase(method)) {
            return "read";
        }

        return "write";
    }

    private String buildKey(HttpServletRequest request, String tier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = null;
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserDetails principal) {
            actor = principal.getUsername();
        }

        if (actor == null || actor.isBlank()) {
            actor = request.getRemoteAddr();
        }

        return "rate-limit:" + tier + ":" + actor;
    }
}
