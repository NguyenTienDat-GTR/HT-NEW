package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

/** Một phạm vi tài khoản được xem: type là diocese | deanery | parish, name là tên đơn vị. */
@Getter @Builder
public class AnalyticsScopeOptionResponse {
    private final String type;
    private final String name;
}
