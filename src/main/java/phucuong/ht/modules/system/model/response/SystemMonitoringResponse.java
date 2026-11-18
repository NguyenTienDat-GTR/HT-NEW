package phucuong.ht.modules.system.model.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Số liệu giám sát request API, tổng hợp từ nhật ký hệ thống.
 * previousSummary là null khi kỳ liền trước vượt quá thời gian lưu nhật ký;
 * rateLimitedRequests là null khi không đọc được bộ đếm trong Redis.
 */
public record SystemMonitoringResponse(
        int hours,
        int bucketMinutes,
        OffsetDateTime from,
        OffsetDateTime generatedAt,
        Summary summary,
        Summary previousSummary,
        Long rateLimitedRequests,
        boolean ipDataReliable,
        List<TimelinePoint> timeline,
        List<IpActivity> topIps,
        List<AccountActivity> topAccounts,
        List<EndpointActivity> busiestEndpoints,
        List<EndpointActivity> slowestEndpoints,
        List<Alert> alerts
) {

    public record Summary(long totalRequests, long serverErrors, long deniedRequests, long failedLogins,
                          long uniqueIps, long uniqueAccounts, long privateIpRequests, Integer p95Ms) {
    }

    public record TimelinePoint(OffsetDateTime time, long requests, long serverErrors, long deniedRequests, Integer p95Ms) {
    }

    public record IpActivity(String ipAddress, long requests, long accounts, long errors, long deniedRequests,
                             long failedLogins, OffsetDateTime lastSeenAt) {
    }

    public record AccountActivity(String username, long requests, long ipAddresses, long errors, OffsetDateTime lastSeenAt) {
    }

    public record EndpointActivity(String method, String path, long requests, long serverErrors, Integer p95Ms) {
    }

    public record Alert(String code, String severity, String title, String detail) {
    }
}
