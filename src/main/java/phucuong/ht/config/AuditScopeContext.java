package phucuong.ht.config;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.util.UUID;

@Getter
@Builder
public class AuditScopeContext {
    private final UnitType unitType;
    private final UUID unitId;
    private final UUID dioceseId;
    private final UUID deaneryId;
    private final UUID parishId;

    /** Không xác định được phạm vi. */
    public static AuditScopeContext empty() {
        return AuditScopeContext.builder().build();
    }
}
