package phucuong.ht.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.notification.service.ToggleNotificationRecipientResolver;
import phucuong.ht.common.dto.UnitStatusHistoryResponse;
import phucuong.ht.common.dto.UnitToggleImpactResponse;
import phucuong.ht.modules.system.service.RbacAuthRefreshNotifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Các bước dùng chung khi khóa/mở giáo phận, giáo hạt, giáo xứ: kiểm tra đơn vị còn hoạt động, ghi lịch sử
 * trạng thái, tính tác động và thông báo. Mọi collaborator đều tùy chọn (thiếu thì bỏ qua bước đó), giống hành vi cũ
 * khi mỗi service tự giữ các field này.
 */
@Component
public class UnitToggleSupport {

    private static final String EVENT = "UNIT_STATUS_TOGGLED";

    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private UnitStatusAuditService unitStatusAuditService;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver recipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher dispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;

    /** Bản không làm gì, dùng khi chạy ngoài Spring (vd. unit test dựng service bằng tay). */
    public static UnitToggleSupport noop() {
        return new UnitToggleSupport();
    }

    public void requireOperational(UnitType unitType, UUID id) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireUnitOperational(unitType, id);
        }
    }

    public void requireToggleAllowed(UnitType unitType, UUID id) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireUnitToggleAllowed(unitType, id);
        }
    }

    public Set<String> recipients(UnitType unitType, UUID id) {
        return recipientResolver == null ? Set.of() : recipientResolver.byUnit(unitType, id);
    }

    /** Ghi nhận ý định khóa/mở (lý do, số tài khoản và đơn vị con bị ảnh hưởng); null nếu chưa cấu hình ghi lịch sử. */
    public UnitStatusAuditService.ToggleContext begin(UnitType unitType, UUID id, String reason) {
        return unitStatusAuditService == null ? null : unitStatusAuditService.begin(unitType, id, reason);
    }

    public void finish(UnitStatusAuditService.ToggleContext context, UnitType unitType, UUID id, String actor) {
        if (context != null && unitStatusAuditService != null) {
            unitStatusAuditService.finish(context, unitType, id, actor);
        }
    }

    /** Làm mới phiên của người dùng bị ảnh hưởng và gửi thông báo khóa/mở đơn vị. */
    public void announce(Set<String> recipients, String unitTypeLabel, String unitName, boolean enabled, String actor) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyUsers(recipients, EVENT);
        }
        if (dispatcher != null) {
            dispatcher.dispatch(recipients, EVENT, unitLabel(unitTypeLabel, unitName), enabled, actor);
        }
    }

    public UnitToggleImpactResponse impact(UnitType unitType, UUID id) {
        if (unitStatusAuditService == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.CONFLICT, "Chưa sẵn sàng tính tác động");
        }
        return unitStatusAuditService.impact(unitType, id);
    }

    public List<UnitStatusHistoryResponse> history(UnitType unitType, UUID id, int page, int size) {
        if (unitStatusAuditService == null) {
            return List.of();
        }
        return unitStatusAuditService.history(unitType, id, page, size);
    }

    static String unitLabel(String unitTypeLabel, String name) {
        String normalizedName = name == null ? null : name.trim();
        return normalizedName == null || normalizedName.isBlank() ? unitTypeLabel : unitTypeLabel + " " + normalizedName;
    }
}
