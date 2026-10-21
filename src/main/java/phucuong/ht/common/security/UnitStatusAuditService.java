package phucuong.ht.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.dto.UnitStatusHistoryResponse;
import phucuong.ht.common.dto.UnitToggleImpactResponse;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.validation.CommonValidationHelper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Ghi lịch sử bật/tắt đơn vị và tính tác động dây chuyền (chỉ đọc) trước khi tắt.
@Service
public class UnitStatusAuditService {

    private static final int MAX_HISTORY_SIZE = 100;

    @PersistenceContext
    private EntityManager entityManager;

    private final UnitOperationalStatusService unitOperationalStatusService;
    private final CommonValidationHelper validationHelper;

    public UnitStatusAuditService(UnitOperationalStatusService unitOperationalStatusService,
                                  CommonValidationHelper validationHelper) {
        this.unitOperationalStatusService = unitOperationalStatusService;
        this.validationHelper = validationHelper;
    }

    public UnitToggleImpactResponse impact(UnitType unitType, UUID unitId) {
        long self = scalar("select count(1) from " + table(unitType) + " where id = :unitId and status = true", unitId);
        long childDeaneries = unitType == UnitType.DIOCESE
                ? scalar("select count(1) from deanery where diocese_id = :unitId and status = true", unitId) : 0;
        long childParishes = switch (unitType) {
            case DIOCESE -> scalar("select count(1) from parish p join deanery de on de.id = p.deanery_id "
                    + "where de.diocese_id = :unitId and p.status = true", unitId);
            case DEANERY -> scalar("select count(1) from parish where deanery_id = :unitId and status = true", unitId);
            case PARISH -> 0;
        };
        long accounts = unitOperationalStatusService.findActiveUsernamesInSubtree(unitType, unitId).size();
        String leaderCond = leaderCondition(unitType);
        long leaders = scalar("select count(1) from leader l where l.status = true and " + leaderCond, unitId);
        long openCourses = switch (unitType) {
            case DIOCESE -> scalar("select count(1) from training_course tc where tc.organizing_status <> 'ENDED' "
                    + "and (tc.diocese_id = :unitId or tc.deanery_id in (select id from deanery where diocese_id = :unitId))", unitId);
            case DEANERY -> scalar("select count(1) from training_course tc where tc.organizing_status <> 'ENDED' "
                    + "and tc.deanery_id = :unitId", unitId);
            case PARISH -> 0;
        };
        long pendingRegistrations = scalar("select count(1) from course_participation cp join leader l on l.id = cp.leader_id "
                + "where cp.approval_status in ('PENDING','SUBMITTED') and " + leaderCond, unitId);
        long pendingCertificates = scalar("select count(1) from certificate c join leader l on l.id = c.leader_id "
                + "where c.approval_status in ('PENDING','SUBMITTED') and " + leaderCond, unitId);
        return new UnitToggleImpactResponse(unitType.name(), self > 0, childDeaneries, childParishes,
                accounts, leaders, openCourses, pendingRegistrations, pendingCertificates);
    }

    // Ngữ cảnh một lần đổi trạng thái: chụp tác động trước khi đổi để ghi vào lịch sử.
    public record ToggleContext(boolean disabling, String reason, long affectedAccounts, long affectedChildUnits) {
    }

    // Gọi trước khi đổi: nếu đang tắt đơn vị đang hoạt động thì bắt buộc có lý do.
    public ToggleContext begin(UnitType unitType, UUID unitId, String reason) {
        UnitToggleImpactResponse impact = impact(unitType, unitId);
        boolean disabling = impact.currentlyActive();
        if (disabling && (reason == null || reason.isBlank())) {
            throw validationHelper.validationError("Vui lòng nhập lý do ngưng hoạt động đơn vị");
        }
        return new ToggleContext(disabling, reason, impact.affectedAccounts(), impact.childDeaneries() + impact.childParishes());
    }

    // Gọi sau khi đổi: ghi dòng lịch sử với trạng thái mới.
    public void finish(ToggleContext context, UnitType unitType, UUID unitId, String actor) {
        record(unitType, unitId, !context.disabling(), context.reason(), actor,
                context.affectedAccounts(), context.affectedChildUnits());
    }

    @Transactional
    public void record(UnitType unitType, UUID unitId, boolean newStatus, String reason, String actor,
                       long affectedAccounts, long affectedChildUnits) {
        entityManager.createNativeQuery("""
                        insert into org_unit_status_history
                            (unit_type, unit_id, status, event_type, reason, changed_by, changed_at,
                             affected_account_count, affected_child_unit_count)
                        values (:unitType, :unitId, :status, 'TOGGLE', :reason, :actor, :changedAt, :accounts, :children)
                        """)
                .setParameter("unitType", unitType.name())
                .setParameter("unitId", unitId)
                .setParameter("status", newStatus)
                .setParameter("reason", reason == null || reason.isBlank() ? null : reason.trim())
                .setParameter("actor", actor)
                .setParameter("changedAt", OffsetDateTime.now())
                .setParameter("accounts", (int) affectedAccounts)
                .setParameter("children", (int) affectedChildUnits)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<UnitStatusHistoryResponse> history(UnitType unitType, UUID unitId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_SIZE);
        int safePage = Math.max(page, 0);
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select cast(id as varchar), unit_type, cast(unit_id as varchar), status, event_type, reason,
                               changed_by, changed_at, affected_account_count, affected_child_unit_count
                        from org_unit_status_history
                        where unit_type = :unitType and unit_id = :unitId
                        order by changed_at desc
                        """)
                .setParameter("unitType", unitType.name())
                .setParameter("unitId", unitId)
                .setFirstResult(safePage * safeSize)
                .setMaxResults(safeSize)
                .getResultList();
        return rows.stream().map(r -> new UnitStatusHistoryResponse(
                (String) r[0], (String) r[1], (String) r[2], Boolean.TRUE.equals(r[3]), (String) r[4], (String) r[5],
                (String) r[6], toOffset(r[7]),
                r[8] == null ? 0 : ((Number) r[8]).intValue(), r[9] == null ? 0 : ((Number) r[9]).intValue())).toList();
    }

    private static OffsetDateTime toOffset(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        }
        return null;
    }

    private static String table(UnitType unitType) {
        return switch (unitType) {
            case DIOCESE -> "diocese";
            case DEANERY -> "deanery";
            case PARISH -> "parish";
        };
    }

    private static String leaderCondition(UnitType unitType) {
        return switch (unitType) {
            case DIOCESE -> "l.parish_id in (select p.id from parish p join deanery de on de.id = p.deanery_id where de.diocese_id = :unitId)";
            case DEANERY -> "l.parish_id in (select p.id from parish p where p.deanery_id = :unitId)";
            case PARISH -> "l.parish_id = :unitId";
        };
    }

    private long scalar(String sql, UUID unitId) {
        Number n = (Number) entityManager.createNativeQuery(sql).setParameter("unitId", unitId).getSingleResult();
        return n.longValue();
    }
}
