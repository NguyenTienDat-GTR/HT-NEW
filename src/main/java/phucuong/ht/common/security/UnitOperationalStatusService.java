package phucuong.ht.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.validation.CommonValidationHelper;

import java.util.*;

@Service
public class UnitOperationalStatusService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CommonValidationHelper validationHelper;

    public UnitOperationalStatusService(CommonValidationHelper validationHelper) {
        this.validationHelper = validationHelper;
    }

    public boolean isAccountUnitLocked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Number count = (Number) entityManager.createNativeQuery("""
                        select count(1)
                        from account a
                        left join diocese ad on ad.id = a.diocese_id
                        left join deanery ade on ade.id = a.deanery_id
                        left join diocese addi on addi.id = ade.diocese_id
                        left join parish ap on ap.id = a.parish_id
                        left join parish app on app.id = ap.parent_parish_id
                        left join deanery apde on apde.id = ap.deanery_id
                        left join diocese apdi on apdi.id = apde.diocese_id
                        left join leader l on l.id = a.leader_id
                        left join parish lp on lp.id = l.parish_id
                        left join parish lpp on lpp.id = lp.parent_parish_id
                        left join deanery lpde on lpde.id = lp.deanery_id
                        left join diocese lpdi on lpdi.id = lpde.diocese_id
                        where a.username = :username
                          and a.status = true
                          and (
                            (a.diocese_id is not null and coalesce(ad.status, false) = false)
                            or (a.diocese_id is null and a.deanery_id is not null
                                and (coalesce(ade.status, false) = false or coalesce(addi.status, false) = false))
                            or (a.diocese_id is null and a.deanery_id is null and a.parish_id is not null
                                and (coalesce(ap.status, false) = false or coalesce(app.status, true) = false or coalesce(apde.status, false) = false or coalesce(apdi.status, false) = false))
                            or (a.diocese_id is null and a.deanery_id is null and a.parish_id is null and a.leader_id is not null
                                and (coalesce(lp.status, false) = false or coalesce(lpp.status, true) = false or coalesce(lpde.status, false) = false or coalesce(lpdi.status, false) = false))
                          )
                        """)
                .setParameter("username", username)
                .getSingleResult();
        return count.longValue() > 0;
    }

    public void requireAccountWritable(String username) {
        if (isAccountUnitLocked(username)) {
            throw validationHelper.forbidden("Tài khoản thuộc đơn vị đang bị khóa, chỉ được đăng nhập và đọc thông báo");
        }
    }

    public boolean isUnitOperational(UnitType unitType, UUID unitId) {
        if (unitType == null || unitId == null) {
            return false;
        }
        String sql = switch (unitType) {
            case DIOCESE -> """
                    select count(1)
                    from diocese d
                    where d.id = :unitId and d.status = true
                    """;
            case DEANERY -> """
                    select count(1)
                    from deanery de
                    join diocese d on d.id = de.diocese_id
                    where de.id = :unitId and de.status = true and d.status = true
                    """;
            case PARISH -> """
                    select count(1)
                    from parish p
                    left join parish pp on pp.id = p.parent_parish_id
                    join deanery de on de.id = p.deanery_id
                    join diocese d on d.id = de.diocese_id
                    where p.id = :unitId and p.status = true and coalesce(pp.status, true) = true and de.status = true and d.status = true
                    """;
        };
        return count(sql, unitId) > 0;
    }

    public void requireUnitOperational(UnitType unitType, UUID unitId) {
        if (!isUnitOperational(unitType, unitId)) {
            throw validationHelper.forbidden("Đơn vị hoặc đơn vị cha đang bị khóa, không thể thực hiện thao tác ghi");
        }
    }

    public void requireUnitToggleAllowed(UnitType unitType, UUID unitId) {
        if (!areAncestorsOperational(unitType, unitId)) {
            throw validationHelper.forbidden("Phải bật lại đơn vị cha trước khi đổi trạng thái đơn vị con");
        }
    }

    public Set<String> findActiveUsernamesInSubtree(UnitType unitType, UUID unitId) {
        if (unitType == null || unitId == null) {
            return Set.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                        select distinct a.username
                        from account a
                        left join deanery ade on ade.id = a.deanery_id
                        left join parish ap on ap.id = a.parish_id
                        left join deanery apde on apde.id = ap.deanery_id
                        left join leader l on l.id = a.leader_id
                        left join parish lp on lp.id = l.parish_id
                        left join deanery lpde on lpde.id = lp.deanery_id
                        where a.status = true
                          and (
                            (:unitType = 'DIOCESE' and (
                                a.diocese_id = :unitId
                                or ade.diocese_id = :unitId
                                or apde.diocese_id = :unitId
                                or lpde.diocese_id = :unitId
                            ))
                            or (:unitType = 'DEANERY' and (
                                a.deanery_id = :unitId
                                or ap.deanery_id = :unitId
                                or lp.deanery_id = :unitId
                            ))
                            or (:unitType = 'PARISH' and (
                                a.parish_id = :unitId
                                or lp.id = :unitId
                                or ap.parent_parish_id = :unitId
                                or lp.parent_parish_id = :unitId
                            ))
                          )
                        """)
                .setParameter("unitType", unitType.name())
                .setParameter("unitId", unitId)
                .getResultList();
        return normalize(rows.stream().map(String::valueOf).toList());
    }

    public Set<String> normalize(Collection<String> usernames) {
        Set<String> normalized = new LinkedHashSet<>();
        if (usernames == null) {
            return normalized;
        }
        for (String username : usernames) {
            if (username != null && !username.isBlank()) {
                normalized.add(username);
            }
        }
        return normalized;
    }

    private boolean areAncestorsOperational(UnitType unitType, UUID unitId) {
        if (unitType == null || unitId == null) {
            return false;
        }
        String sql = switch (unitType) {
            case DIOCESE -> """
                    select count(1)
                    from diocese d
                    where d.id = :unitId
                    """;
            case DEANERY -> """
                    select count(1)
                    from deanery de
                    join diocese d on d.id = de.diocese_id
                    where de.id = :unitId and d.status = true
                    """;
            case PARISH -> """
                    select count(1)
                    from parish p
                    left join parish pp on pp.id = p.parent_parish_id
                    join deanery de on de.id = p.deanery_id
                    join diocese d on d.id = de.diocese_id
                    where p.id = :unitId and coalesce(pp.status, true) = true and de.status = true and d.status = true
                    """;
        };
        return count(sql, unitId) > 0;
    }

    private long count(String sql, UUID unitId) {
        Number count = (Number) entityManager.createNativeQuery(sql)
                .setParameter("unitId", unitId)
                .getSingleResult();
        return count.longValue();
    }
}
