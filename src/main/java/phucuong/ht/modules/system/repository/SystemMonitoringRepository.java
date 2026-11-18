package phucuong.ht.modules.system.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.AccountActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.EndpointActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.IpActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Summary;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.TimelinePoint;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/** Truy vấn tổng hợp trên system_log; chính các lần gọi trang giám sát không được tính vào số liệu. */
@Repository
@RequiredArgsConstructor
public class SystemMonitoringRepository {

    /** Địa chỉ nội bộ / loopback: nếu chiếm gần hết thì BE đang không nhận được IP thật của người dùng. */
    public static final String PRIVATE_IP_REGEX =
            "^(10\\.|127\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|::1$|0:0:0:0:0:0:0:1$|[fF][cCdD][0-9a-fA-F]{2}:)";

    private static final String IN_WINDOW = """
            logged_at >= CAST(:from AS timestamptz) AND logged_at < CAST(:to AS timestamptz)
              AND request_path NOT LIKE '/api/system/monitoring/%'
            """;
    private static final String IS_DENIED = "status_code IN (401, 403)";
    private static final String IS_FAILED_LOGIN =
            "request_path IN ('/api/auth/login', '/api/mobile/auth/login') AND status_code >= 400";
    private static final String P95 = "CAST(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS integer)";
    private static final String NORMALIZED_PATH = """
            regexp_replace(request_path,
                '/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9]+)(?=/|$)', '/{id}', 'g')
            """;
    private static final String BUCKET_ORIGIN = "TIMESTAMPTZ '2000-01-01 00:00:00+07'";

    private static final RowMapper<IpActivity> IP_ROW = (rs, i) -> new IpActivity(
            rs.getString("ip_address"), rs.getLong("requests"), rs.getLong("accounts"), rs.getLong("errors"),
            rs.getLong("denied"), rs.getLong("failed_logins"), rs.getObject("last_seen", OffsetDateTime.class));

    private final NamedParameterJdbcTemplate jdbc;

    public Summary summary(OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE status_code >= 500) AS server_errors,
                       count(*) FILTER (WHERE %s) AS denied,
                       count(*) FILTER (WHERE %s) AS failed_logins,
                       count(DISTINCT ip_address) AS unique_ips,
                       count(DISTINCT username) AS unique_accounts,
                       count(*) FILTER (WHERE ip_address ~ :privateIp) AS private_ip_requests,
                       %s AS p95_ms
                FROM system_log
                WHERE %s
                """.formatted(IS_DENIED, IS_FAILED_LOGIN, P95, IN_WINDOW);
        return jdbc.queryForObject(sql, window(from, to).addValue("privateIp", PRIVATE_IP_REGEX), (rs, i) -> new Summary(
                rs.getLong("total"), rs.getLong("server_errors"), rs.getLong("denied"), rs.getLong("failed_logins"),
                rs.getLong("unique_ips"), rs.getLong("unique_accounts"), rs.getLong("private_ip_requests"), integer(rs, "p95_ms")));
    }

    /** Mỗi mốc thời gian đều có một dòng, kể cả khi không có request, để biểu đồ không nối liền qua khoảng trống. */
    public List<TimelinePoint> timeline(OffsetDateTime from, OffsetDateTime to, int bucketMinutes) {
        String sql = """
                WITH buckets AS (
                    SELECT generate_series(
                               date_bin(make_interval(mins => :bucketMinutes), CAST(:from AS timestamptz), %1$s),
                               CAST(:to AS timestamptz),
                               make_interval(mins => :bucketMinutes)) AS bucket
                ), stats AS (
                    SELECT date_bin(make_interval(mins => :bucketMinutes), logged_at, %1$s) AS bucket,
                           count(*) AS requests,
                           count(*) FILTER (WHERE status_code >= 500) AS server_errors,
                           count(*) FILTER (WHERE %2$s) AS denied,
                           %3$s AS p95_ms
                    FROM system_log
                    WHERE %4$s
                    GROUP BY 1
                )
                SELECT b.bucket, COALESCE(s.requests, 0) AS requests, COALESCE(s.server_errors, 0) AS server_errors,
                       COALESCE(s.denied, 0) AS denied, s.p95_ms
                FROM buckets b LEFT JOIN stats s ON s.bucket = b.bucket
                ORDER BY b.bucket
                """.formatted(BUCKET_ORIGIN, IS_DENIED, P95, IN_WINDOW);
        return jdbc.query(sql, window(from, to).addValue("bucketMinutes", bucketMinutes), (rs, i) -> new TimelinePoint(
                rs.getObject("bucket", OffsetDateTime.class), rs.getLong("requests"), rs.getLong("server_errors"),
                rs.getLong("denied"), integer(rs, "p95_ms")));
    }

    public List<IpActivity> topIps(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.query(ipSql("") + " ORDER BY requests DESC, ip_address LIMIT :limit",
                window(from, to).addValue("limit", limit), IP_ROW);
    }

    /** IP vượt một trong các ngưỡng trong khoảng thời gian, dùng để sinh cảnh báo. */
    public List<IpActivity> suspiciousIps(OffsetDateTime from, OffsetDateTime to, int failedLogins, int denied, int requests, int limit) {
        String having = """
                HAVING count(*) FILTER (WHERE %s) >= :failedLogins
                    OR count(*) FILTER (WHERE %s) >= :denied
                    OR count(*) >= :requests
                """.formatted(IS_FAILED_LOGIN, IS_DENIED);
        return jdbc.query(ipSql(having) + " ORDER BY failed_logins DESC, denied DESC, requests DESC LIMIT :limit",
                window(from, to).addValue("failedLogins", failedLogins).addValue("denied", denied)
                        .addValue("requests", requests).addValue("limit", limit), IP_ROW);
    }

    public List<AccountActivity> topAccounts(OffsetDateTime from, OffsetDateTime to, int limit) {
        String sql = """
                SELECT username, count(*) AS requests, count(DISTINCT ip_address) AS ip_addresses,
                       count(*) FILTER (WHERE status_code >= 400) AS errors, max(logged_at) AS last_seen
                FROM system_log
                WHERE %s AND username IS NOT NULL
                GROUP BY username
                ORDER BY requests DESC, username
                LIMIT :limit
                """.formatted(IN_WINDOW);
        return jdbc.query(sql, window(from, to).addValue("limit", limit), (rs, i) -> new AccountActivity(
                rs.getString("username"), rs.getLong("requests"), rs.getLong("ip_addresses"), rs.getLong("errors"),
                rs.getObject("last_seen", OffsetDateTime.class)));
    }

    public List<EndpointActivity> busiestEndpoints(OffsetDateTime from, OffsetDateTime to, int limit) {
        return endpoints(from, to, 1, "requests DESC, p95_ms DESC NULLS LAST", limit);
    }

    /** Chỉ xét đường dẫn có đủ số request để p95 có ý nghĩa. */
    public List<EndpointActivity> slowestEndpoints(OffsetDateTime from, OffsetDateTime to, int minRequests, int limit) {
        return endpoints(from, to, minRequests, "p95_ms DESC NULLS LAST, requests DESC", limit);
    }

    /** Trung bình số request mỗi giờ (chỉ tính các giờ có dữ liệu); 0 nếu chưa có lịch sử. */
    public double averageRequestsPerActiveHour(OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT COALESCE(CAST(count(*) AS double precision) / NULLIF(count(DISTINCT date_trunc('hour', logged_at)), 0), 0)
                FROM system_log
                WHERE %s
                """.formatted(IN_WINDOW);
        Double average = jdbc.queryForObject(sql, window(from, to), Double.class);
        return average == null ? 0 : average;
    }

    private List<EndpointActivity> endpoints(OffsetDateTime from, OffsetDateTime to, int minRequests, String orderBy, int limit) {
        String sql = """
                SELECT http_method, %s AS path, count(*) AS requests,
                       count(*) FILTER (WHERE status_code >= 500) AS server_errors, %s AS p95_ms
                FROM system_log
                WHERE %s
                GROUP BY 1, 2
                HAVING count(*) >= :minRequests
                ORDER BY %s
                LIMIT :limit
                """.formatted(NORMALIZED_PATH, P95, IN_WINDOW, orderBy);
        return jdbc.query(sql, window(from, to).addValue("minRequests", minRequests).addValue("limit", limit),
                (rs, i) -> new EndpointActivity(rs.getString("http_method"), rs.getString("path"), rs.getLong("requests"),
                        rs.getLong("server_errors"), integer(rs, "p95_ms")));
    }

    private static String ipSql(String having) {
        return """
                SELECT ip_address, count(*) AS requests, count(DISTINCT username) AS accounts,
                       count(*) FILTER (WHERE status_code >= 400) AS errors,
                       count(*) FILTER (WHERE %s) AS denied,
                       count(*) FILTER (WHERE %s) AS failed_logins,
                       max(logged_at) AS last_seen
                FROM system_log
                WHERE %s AND ip_address IS NOT NULL
                GROUP BY ip_address
                %s
                """.formatted(IS_DENIED, IS_FAILED_LOGIN, IN_WINDOW, having);
    }

    private static MapSqlParameterSource window(OffsetDateTime from, OffsetDateTime to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
