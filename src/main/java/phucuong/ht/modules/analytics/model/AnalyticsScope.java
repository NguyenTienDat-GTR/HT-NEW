package phucuong.ht.modules.analytics.model;

import java.util.UUID;

public record AnalyticsScope(AnalyticsScopeType type, UUID dioceseId, UUID deaneryId, UUID parishId) {
    public boolean isSystemOverview() { return type == AnalyticsScopeType.ALL; }
}
