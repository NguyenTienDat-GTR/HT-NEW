package phucuong.ht.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.SystemLog;
import phucuong.ht.modules.system.repository.SystemLogRepository;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Component
public class ApiAuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAuditLoggingFilter.class);
    private static final int REQUEST_PATH_MAX_LENGTH = 500;
    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final int QUERY_STRING_MAX_LENGTH = 1000;
    private static final int RESOURCE_ID_MAX_LENGTH = 255;
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    // Tham số có thể chứa bí mật: không bao giờ ghi vào nhật ký.
    private static final Set<String> SENSITIVE_PARAMS = Set.of("password", "token", "otp", "secret", "accesstoken", "refreshtoken", "code");
    // Tài nguyên mà định danh là tên chữ (tên đăng nhập, mã vai trò...) chứ không chứa chữ số.
    private static final Set<String> NAMED_ID_RESOURCES = Set.of("account", "role", "permission");

    private final SystemLogRepository systemLogRepository;
    private final AuditScopeResolver auditScopeResolver;
    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final AuditResourceNamer auditResourceNamer;
    // Lưu nhật ký sau khi đã trả phản hồi: tra phạm vi / tên đối tượng và insert không làm chậm request (mỗi câu SQL vài chục ms khi DB ở xa).
    private final Executor auditExecutor;

    /** Lưu đồng bộ ngay trong request (dùng cho test đơn vị). */
    public ApiAuditLoggingFilter(SystemLogRepository systemLogRepository, AuditScopeResolver auditScopeResolver, securityUtils securityUtils, AccountRepository accountRepository, AuditResourceNamer auditResourceNamer) {
        this(systemLogRepository, auditScopeResolver, securityUtils, accountRepository, auditResourceNamer, Runnable::run);
    }

    @Autowired
    public ApiAuditLoggingFilter(SystemLogRepository systemLogRepository, AuditScopeResolver auditScopeResolver, securityUtils securityUtils, AccountRepository accountRepository, AuditResourceNamer auditResourceNamer,
                                 @Qualifier(AsyncConfig.AUDIT_LOG_EXECUTOR) Executor auditExecutor) {
        this.systemLogRepository = systemLogRepository;
        this.auditScopeResolver = auditScopeResolver;
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
        this.auditResourceNamer = auditResourceNamer;
        this.auditExecutor = auditExecutor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        String resource = resolveResource(request.getRequestURI());
        String resourceId = extractResourceId(request.getRequestURI(), resource);
        boolean read = isRead(request.getMethod());
        // Request ghi: tra tên TRƯỚC khi xử lý để nhật ký giữ tên của đối tượng như lúc bị tác động (sửa / xóa).
        // Request đọc không đổi dữ liệu nên tra tên sau, ở luồng nền.
        String resourceName = read ? null : safeName(resource, resourceId);

        filterChain.doFilter(request, response);

        AuditEntry entry;
        try {
            entry = snapshot(request, response, resource, resourceId, resourceName, read, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException exception) {
            log.warn("Không thể ghi nhận audit log cho {} {}", request.getMethod(), request.getRequestURI(), exception);
            return;
        }
        try {
            auditExecutor.execute(() -> persistQuietly(entry));
        } catch (RuntimeException rejected) {
            // Hàng đợi từ chối (vd. đang tắt ứng dụng): lưu ngay để không mất nhật ký.
            persistQuietly(entry);
        }
    }

    private static boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    /** Mọi thông tin lấy từ request / phản hồi, chụp lại trước khi request kết thúc (luồng nền không đọc được request nữa). */
    private record AuditEntry(Authentication authentication, String method, String uri, String query, int status, long durationMs,
                              String resource, String resourceId, String resourceName, boolean resolveName,
                              String ipAddress, String userAgent, OffsetDateTime loggedAt) {
    }

    private AuditEntry snapshot(HttpServletRequest request, HttpServletResponse response, String resource, String resourceId,
                                String resourceName, boolean resolveName, long durationMs) {
        return new AuditEntry(SecurityContextHolder.getContext().getAuthentication(), request.getMethod(), request.getRequestURI(),
                sanitizeQuery(request), response.getStatus(), durationMs, resource, resourceId, resourceName, resolveName,
                request.getRemoteAddr(), request.getHeader("User-Agent"), OffsetDateTime.now());
    }

    private void persistQuietly(AuditEntry entry) {
        try {
            persistAuditLog(entry);
        } catch (RuntimeException exception) {
            log.warn("Không thể lưu audit log cho {} {}", entry.method(), entry.uri(), exception);
        }
    }

    private String safeName(String resource, String resourceId) {
        try {
            return auditResourceNamer.nameOf(resource, resourceId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void persistAuditLog(AuditEntry entry) {
        Authentication authentication = entry.authentication();
        String method = entry.method();
        String uri = entry.uri();
        String resource = entry.resource();
        String resourceId = entry.resourceId();
        String resourceName = entry.resolveName() ? safeName(resource, resourceId) : entry.resourceName();
        String currentUsername = securityUtils.currentUsername(authentication);
        String persistedUsername = currentUsername != null && accountRepository.existsByUsername(currentUsername) ? currentUsername : null;
        AuditScopeContext scope = auditScopeResolver.resolve(authentication, uri, resource, resourceId);

        SystemLog log = new SystemLog();
        log.setAction(method);
        log.setHttpMethod(method);
        log.setOperation(resolveOperation(method, uri, resource, resourceId));
        log.setRequestPath(truncate(uri, REQUEST_PATH_MAX_LENGTH));
        log.setQueryString(truncate(entry.query(), QUERY_STRING_MAX_LENGTH));
        log.setStatusCode(entry.status());
        log.setDurationMs((int) Math.min(entry.durationMs(), Integer.MAX_VALUE));
        log.setModule(resolveModule(uri));
        log.setResource(resource);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setIpAddress(entry.ipAddress());
        log.setUserAgent(truncate(entry.userAgent(), USER_AGENT_MAX_LENGTH));
        log.setMessage(method + " " + truncate(uri, REQUEST_PATH_MAX_LENGTH));
        log.setLoggedAt(entry.loggedAt());
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

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    /** Bộ lọc của yêu cầu (vd. "status=true&size=20") bỏ các tham số có thể chứa bí mật; rỗng thì null. */
    static String sanitizeQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) return null;
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            String name = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (!SENSITIVE_PARAMS.contains(name.toLowerCase(Locale.ROOT))) kept.add(pair);
        }
        return kept.isEmpty() ? null : String.join("&", kept);
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
        if (path.startsWith("/api/analytics")) {
            return "analytics";
        }
        if (path.startsWith("/api/auth")) {
            return "auth";
        }
        return "organization";
    }

    /** Khóa đối tượng của đường dẫn; đường dẫn lạ thì lấy đoạn tài nguyên đầu tiên thay vì để trống. */
    static String resolveResource(String path) {
        if (path.startsWith("/api/dioceses")) return "diocese";
        if (path.startsWith("/api/deaneries")) return "deanery";
        if (path.startsWith("/api/parishes")) return "parish";
        if (path.startsWith("/api/leaders")) return "leader";
        if (path.startsWith("/api/positions")) return "position";
        if (path.startsWith("/api/leader-levels")) return "leader_level";
        if (path.startsWith("/api/training/courses")) return "course";
        if (path.startsWith("/api/training/course-types")) return "course_type";
        if (path.startsWith("/api/training/requirements")) return "requirement";
        if (path.startsWith("/api/training/registration-template")) return "registration_template";
        if (path.startsWith("/api/training/participations") || path.startsWith("/api/training/registrations")) return "participation";
        if (path.startsWith("/api/certificates/config")) return "certificate_config";
        if (path.startsWith("/api/certificates")) return "certificate";
        if (path.startsWith("/api/system/audit-logs")) return "audit_log";
        if (path.startsWith("/api/system/accounts")) return "account";
        if (path.startsWith("/api/system/roles")) return "role";
        if (path.startsWith("/api/system/permissions")) return "permission";
        if (path.startsWith("/api/system/notifications")) return "notification";
        if (path.startsWith("/api/executive-board/assignments")) return "executive_board_assignment";
        if (path.startsWith("/api/analytics")) return "analytics";
        if (path.startsWith("/api/auth")) return "auth";
        String[] segments = path.split("/");
        // /api/{module}/{resource}/...: "system" và "training" là phân hệ nên lấy đoạn kế tiếp.
        int index = segments.length > 3 && ("system".equals(segments[2]) || "training".equals(segments[2])) ? 3 : 2;
        return segments.length > index && !segments[index].isBlank() ? segments[index].replace('-', '_').toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Mã đối tượng nằm ngay sau tên tài nguyên trong đường dẫn. Chỉ nhận chuỗi giống mã (UUID, số, hoặc có chữ số) để các đoạn như
     * "me", "options", "filter-options" không bị ghi nhầm thành mã đối tượng.
     */
    static String extractResourceId(String path, String resource) {
        String[] segments = path.split("/");
        int index = segments.length > 3 && path.matches("^/api/(system|training)/.*") ? 4 : 3;
        if (segments.length <= index) return null;
        String candidate = segments[index];
        if (candidate.isBlank() || candidate.length() > RESOURCE_ID_MAX_LENGTH) return null;
        if (UUID_PATTERN.matcher(candidate).matches() || candidate.chars().anyMatch(Character::isDigit)) return candidate;
        return NAMED_ID_RESOURCES.contains(resource) ? candidate : null;
    }

    /** Thao tác nghiệp vụ: LIST, VIEW, CREATE, UPDATE, DELETE hoặc hành động riêng ở cuối đường dẫn (APPROVE, SUBMIT, TOGGLE_STATUS, LOGIN...). */
    static String resolveOperation(String method, String path, String resource, String resourceId) {
        String upperMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String[] segments = path.split("/");
        int base = path.matches("^/api/(system|training)/.*") ? 4 : 3;
        int extra = Math.max(0, segments.length - base);
        boolean read = "GET".equals(upperMethod) || "HEAD".equals(upperMethod);
        if (extra == 0) return read ? "LIST" : defaultOperation(upperMethod);
        if (read) return "VIEW";
        // Có mã đối tượng thì hành động nằm ở đoạn sau mã; không có mã thì đoạn cuối là tên chức năng (login, logout...).
        boolean hasVerb = resourceId == null || extra >= 2;
        if (hasVerb && ("POST".equals(upperMethod) || "PUT".equals(upperMethod) || "PATCH".equals(upperMethod))) {
            return segments[segments.length - 1].replace('-', '_').toUpperCase(Locale.ROOT);
        }
        return defaultOperation(upperMethod);
    }

    private static String defaultOperation(String method) {
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method;
        };
    }
}
