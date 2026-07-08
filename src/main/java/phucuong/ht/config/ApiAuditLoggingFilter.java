package phucuong.ht.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.SystemLog;
import phucuong.ht.modules.system.repository.SystemLogRepository;

import java.io.IOException;
import java.time.OffsetDateTime;
@Component
public class ApiAuditLoggingFilter extends OncePerRequestFilter {

    private final SystemLogRepository systemLogRepository;
    private final AuditScopeResolver auditScopeResolver;
    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    public ApiAuditLoggingFilter(SystemLogRepository systemLogRepository, AuditScopeResolver auditScopeResolver, securityUtils securityUtils, AccountRepository accountRepository) {
        this.systemLogRepository = systemLogRepository;
        this.auditScopeResolver = auditScopeResolver;
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = securityUtils.currentUsername(authentication);
        String persistedUsername = currentUsername != null && accountRepository.existsByUsername(currentUsername) ? currentUsername : null;
        String resourceId = extractResourceId(request.getRequestURI());
        String resource = resolveResource(request.getRequestURI());
        AuditScopeContext scope = auditScopeResolver.resolve(authentication, request.getRequestURI(), resource, resourceId);

        SystemLog log = new SystemLog();
        log.setAction(request.getMethod());
        log.setHttpMethod(request.getMethod());
        log.setRequestPath(request.getRequestURI());
        log.setStatusCode(response.getStatus());
        log.setModule(resolveModule(request.getRequestURI()));
        log.setResource(resource);
        log.setResourceId(resourceId);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setMessage(request.getMethod() + " " + request.getRequestURI());
        log.setLoggedAt(OffsetDateTime.now());
        log.setStatus(Boolean.TRUE);
        log.setCreatedAt(log.getLoggedAt());
        log.setUpdatedAt(log.getLoggedAt());
        log.setCreatedBy(currentUsername);
        log.setUpdatedBy(currentUsername);
        log.setUsername(persistedUsername);
        log.setUnitType(scope.getUnitType());
        log.setUnitId(scope.getUnitId());
        log.setDioceseId(scope.getDioceseId());
        log.setDeaneryId(scope.getDeaneryId());
        log.setParishId(scope.getParishId());
        systemLogRepository.save(log);
    }

    private String resolveModule(String path) {
        if (path.startsWith("/api/system")) {
            return "system";
        }
        if (path.startsWith("/api/training")) {
            return "training";
        }
        if (path.startsWith("/api/certificates")) {
            return "training";
        }
        return "organization";
    }

    private String resolveResource(String path) {
        if (path.startsWith("/api/dioceses")) {
            return "diocese";
        }
        if (path.startsWith("/api/deaneries")) {
            return "deanery";
        }
        if (path.startsWith("/api/parishes")) {
            return "parish";
        }
        if (path.startsWith("/api/leaders")) {
            return "leader";
        }
        if (path.startsWith("/api/positions")) {
            return "position";
        }
        if (path.startsWith("/api/training/courses")) {
            return "course";
        }
        if (path.startsWith("/api/training/requirements")) {
            return "requirement";
        }
        if (path.startsWith("/api/certificates")) {
            return "certificate";
        }
        if (path.startsWith("/api/system/audit-logs")) {
            return "audit_log";
        }
        if (path.startsWith("/api/executive-board/assignments")) {
            return "executive_board_assignment";
        }
        return null;
    }

    private String extractResourceId(String path) {
        String[] segments = path.split("/");
        if (segments.length < 4) {
            return null;
        }
        String candidate = segments[segments.length - 1];
        if ("toggle-status".equals(candidate) && segments.length >= 5) {
            return segments[segments.length - 2];
        }
        if ("submit".equals(candidate) || "approve".equals(candidate) || "score".equals(candidate)) {
            return segments.length >= 5 ? segments[segments.length - 2] : null;
        }
        return candidate;
    }
}
