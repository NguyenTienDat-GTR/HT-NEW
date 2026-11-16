package phucuong.ht.modules.system.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Xác định "nơi áp dụng" của một thao tác phân quyền vai trò (mặc định chung hay một giáo phận / giáo hạt / giáo xứ)
 * và kiểm tra người thao tác có được làm việc đó không:
 * <ul>
 *   <li>SUPER_ADMIN: mặc định chung hoặc cả một giáo phận, mọi quyền, vai trò hệ thống (trừ SUPER_ADMIN) hoặc do SUPER_ADMIN tạo;</li>
 *   <li>admin giáo phận: ADMIN_DEANERY (cả giáo phận hoặc một giáo hạt) và ADMIN_PARISH (cả giáo phận, một giáo hạt hoặc một giáo xứ)
 *       trong giáo phận của mình, chỉ với quyền phạm vi giáo hạt / giáo xứ;</li>
 *   <li>admin giáo hạt: ADMIN_PARISH (cả giáo hạt hoặc một giáo xứ) trong giáo hạt của mình, chỉ với quyền phạm vi giáo xứ.</li>
 * </ul>
 * Vai trò khác (vai trò tự tạo của từng đơn vị) giữ nguyên đường xử lý cũ, không có ngoại lệ theo đơn vị.
 */
@Component
@RequiredArgsConstructor
public class RolePermissionUnitScopeResolver {

    static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    static final String ROLE_ADMIN_DEANERY = "ADMIN_DEANERY";
    static final String ROLE_ADMIN_PARISH = "ADMIN_PARISH";

    private final SystemAdminAuthorizationHelper authorizationHelper;
    private final AccountRepository accountRepository;
    private final securityUtils securityUtils;
    private final CommonValidationHelper commonValidationHelper;

    @PersistenceContext
    private EntityManager entityManager;

    public enum ActorTier { SUPER_ADMIN, DIOCESE, DEANERY, NONE }

    /** Người thao tác: cấp và đơn vị quản lý của họ (tính một lần cho cả một request). */
    public record Actor(ActorTier tier, UUID dioceseId, UUID deaneryId) {
    }

    /** Một đơn vị cụ thể cùng chuỗi cha của nó. */
    public record UnitScope(RolePermissionScopeLevel level, UUID dioceseId, String dioceseName,
                            UUID deaneryId, String deaneryName, UUID parishId, String parishName) {

        public UUID unitId() {
            return switch (level) {
                case DIOCESE -> dioceseId;
                case DEANERY -> deaneryId;
                case PARISH -> parishId;
            };
        }

        /** Đơn vị cha và chính nó, từ rộng đến hẹp. */
        public List<UUID> chain() {
            List<UUID> ids = new ArrayList<>(ancestors());
            ids.add(unitId());
            return ids;
        }

        /** Các đơn vị cấp trên của nó (không gồm chính nó). */
        public List<UUID> ancestors() {
            List<UUID> ids = new ArrayList<>();
            if (level != RolePermissionScopeLevel.DIOCESE) {
                ids.add(dioceseId);
            }
            if (level == RolePermissionScopeLevel.PARISH) {
                ids.add(deaneryId);
            }
            return ids;
        }

        /** "giáo phận X" / "giáo hạt X" / "giáo xứ X" để ghép vào thông báo và lời nhắn. */
        public String label() {
            return switch (level) {
                case DIOCESE -> "giáo phận " + dioceseName;
                case DEANERY -> "giáo hạt " + deaneryName;
                case PARISH -> "giáo xứ " + parishName;
            };
        }

        public String displayName() {
            String label = label();
            return Character.toUpperCase(label.charAt(0)) + label.substring(1);
        }
    }

    public Actor actor(Authentication authentication, boolean revoke) {
        if (authorizationHelper.isSuperAdmin(authentication)) {
            return new Actor(ActorTier.SUPER_ADMIN, null, null);
        }
        String suffix = revoke ? "revoke" : "assign";
        String username = securityUtils.currentUsername(authentication);
        if (authorizationHelper.hasAuthority(authentication, "ROLE_" + ROLE_ADMIN_DIOCESE)
                && authorizationHelper.hasAuthority(authentication, "system.role_permission." + suffix + ".diocese")) {
            UUID diocese = username == null ? null : accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
            return new Actor(ActorTier.DIOCESE, diocese, null);
        }
        if (authorizationHelper.hasAuthority(authentication, "ROLE_" + ROLE_ADMIN_DEANERY)
                && authorizationHelper.hasAuthority(authentication, "system.role_permission." + suffix + ".deanery")) {
            UUID diocese = username == null ? null : accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
            UUID deanery = username == null ? null : accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
            return new Actor(ActorTier.DEANERY, diocese, deanery);
        }
        return new Actor(ActorTier.NONE, null, null);
    }

    /** Vai trò có hỗ trợ ngoại lệ theo đơn vị đối với người thao tác này không. */
    public boolean roleEligible(Actor actor, Role role) {
        String code = role == null ? null : role.getRoleCode();
        return switch (actor.tier()) {
            case SUPER_ADMIN -> role != null && !ROLE_SUPER_ADMIN.equals(code) && authorizationHelper.isManageableBySuperAdmin(role);
            case DIOCESE -> isSystem(role) && (ROLE_ADMIN_DEANERY.equals(code) || ROLE_ADMIN_PARISH.equals(code));
            case DEANERY -> isSystem(role) && ROLE_ADMIN_PARISH.equals(code);
            case NONE -> false;
        };
    }

    private static boolean isSystem(Role role) {
        return role != null && Boolean.TRUE.equals(role.getIsSystem());
    }

    /**
     * Nơi áp dụng của thao tác: rỗng nghĩa là đường xử lý cũ (mặc định chung, hoặc vai trò tự tạo của đơn vị);
     * ngược lại là đơn vị đã kiểm tra quyền. Ném lỗi 403/404/409 kèm thông báo tiếng Việt khi không hợp lệ.
     */
    public Optional<UnitScope> resolve(Authentication authentication, Role role, UUID dioceseId, UUID deaneryId, UUID parishId, boolean revoke) {
        boolean anyUnit = dioceseId != null || deaneryId != null || parishId != null;
        Actor actor = actor(authentication, revoke);
        if (actor.tier() == ActorTier.SUPER_ADMIN) {
            if (!anyUnit) {
                return Optional.empty();
            }
            if (deaneryId != null || parishId != null) {
                throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được phân quyền riêng tới cấp giáo phận; cấp giáo hạt, giáo xứ do admin giáo phận thiết lập");
            }
            if (role != null && ROLE_SUPER_ADMIN.equals(role.getRoleCode())) {
                throw commonValidationHelper.conflict("Vai trò SUPER_ADMIN không thuộc giáo phận nào");
            }
            authorizationHelper.requireManageableBySuperAdmin(role);
            return Optional.of(load(dioceseId, null, null));
        }
        if (!roleEligible(actor, role)) {
            if (anyUnit) {
                throw commonValidationHelper.forbidden("Bạn không có quyền phân quyền riêng theo giáo phận, giáo hạt, giáo xứ cho vai trò này");
            }
            return Optional.empty();
        }
        UnitScope target;
        if (anyUnit) {
            target = load(dioceseId, deaneryId, parishId);
        } else if (actor.tier() == ActorTier.DIOCESE) {
            target = load(requireOwn(actor.dioceseId()), null, null);
        } else {
            target = load(null, requireOwn(actor.deaneryId()), null);
        }
        requireTargetAllowed(actor, role, target.level(), target.dioceseId(), target.deaneryId());
        return Optional.of(target);
    }

    /** Một dòng ngoại lệ có thuộc quyền thao tác của người này không (dùng khi tính "được thu hồi" trên danh sách). */
    public boolean canManageRow(Actor actor, Role role, Permission permission, RolePermissionScopeLevel level, UUID dioceseId, UUID deaneryId) {
        if (actor.tier() == ActorTier.NONE || level == null || !roleEligible(actor, role)) {
            return false;
        }
        try {
            requireTargetAllowed(actor, role, level, dioceseId, deaneryId);
            requirePermissionAllowed(actor, permission);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void requireTargetAllowed(Actor actor, Role role, RolePermissionScopeLevel level, UUID dioceseId, UUID deaneryId) {
        switch (actor.tier()) {
            case SUPER_ADMIN -> {
                if (level != RolePermissionScopeLevel.DIOCESE) {
                    throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được phân quyền riêng tới cấp giáo phận");
                }
            }
            case DIOCESE -> {
                if (actor.dioceseId() == null || !actor.dioceseId().equals(dioceseId)) {
                    throw commonValidationHelper.forbidden("Chỉ được phân quyền trong giáo phận của mình");
                }
                if (ROLE_ADMIN_DEANERY.equals(role.getRoleCode()) && level == RolePermissionScopeLevel.PARISH) {
                    throw commonValidationHelper.forbidden("Vai trò ADMIN_DEANERY chỉ được phân quyền riêng tới cấp giáo phận hoặc giáo hạt");
                }
            }
            case DEANERY -> {
                if (level == RolePermissionScopeLevel.DIOCESE || actor.deaneryId() == null || !actor.deaneryId().equals(deaneryId)) {
                    throw commonValidationHelper.forbidden("Chỉ được phân quyền trong giáo hạt của mình");
                }
            }
            case NONE -> throw commonValidationHelper.forbidden("Không có quyền thay đổi phân quyền cho vai trò");
        }
    }

    /** Quyền được chọn có nằm trong giới hạn phạm vi của người thao tác không. */
    public void requirePermissionAllowed(Actor actor, Permission permission) {
        String scope = permission == null || permission.getScope() == null || permission.getScope().getScopeCode() == null
                ? "" : permission.getScope().getScopeCode().toLowerCase();
        switch (actor.tier()) {
            case SUPER_ADMIN -> {
            }
            case DIOCESE -> {
                if (!scope.equals("deanery") && !scope.equals("parish")) {
                    throw commonValidationHelper.forbidden("Admin giáo phận chỉ được thêm hoặc thu hồi quyền thuộc phạm vi giáo hạt, giáo xứ");
                }
            }
            case DEANERY -> {
                if (!scope.equals("parish")) {
                    throw commonValidationHelper.forbidden("Admin giáo hạt chỉ được thêm hoặc thu hồi quyền thuộc phạm vi giáo xứ");
                }
            }
            case NONE -> throw commonValidationHelper.forbidden("Không có quyền thay đổi phân quyền cho vai trò");
        }
    }

    /** Không xét quyền: dùng cho các hàm chỉ cần lọc danh sách. */
    public boolean permissionAllowed(Actor actor, Permission permission) {
        try {
            requirePermissionAllowed(actor, permission);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private UUID requireOwn(UUID ownUnitId) {
        if (ownUnitId == null) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi đơn vị hợp lệ");
        }
        return ownUnitId;
    }

    /** Tải đơn vị cụ thể nhất trong ba id gửi lên cùng chuỗi cha, và kiểm tra các id kia khớp với chuỗi đó. */
    public UnitScope load(UUID dioceseId, UUID deaneryId, UUID parishId) {
        if (parishId != null) {
            List<?> rows = entityManager.createNativeQuery("""
                    select p.id, p.name, de.id, de.name, di.id, di.name
                    from parish p
                    join deanery de on de.id = p.deanery_id
                    join diocese di on di.id = de.diocese_id
                    where p.id = :id
                    """).setParameter("id", parishId).getResultList();
            if (rows.isEmpty()) {
                throw commonValidationHelper.notFound("Không tìm thấy giáo xứ");
            }
            Object[] row = (Object[]) rows.get(0);
            UnitScope scope = new UnitScope(RolePermissionScopeLevel.PARISH,
                    uuid(row[4]), String.valueOf(row[5]), uuid(row[2]), String.valueOf(row[3]), uuid(row[0]), String.valueOf(row[1]));
            requireConsistent(scope, dioceseId, deaneryId);
            return scope;
        }
        if (deaneryId != null) {
            List<?> rows = entityManager.createNativeQuery("""
                    select de.id, de.name, di.id, di.name
                    from deanery de
                    join diocese di on di.id = de.diocese_id
                    where de.id = :id
                    """).setParameter("id", deaneryId).getResultList();
            if (rows.isEmpty()) {
                throw commonValidationHelper.notFound("Không tìm thấy giáo hạt");
            }
            Object[] row = (Object[]) rows.get(0);
            UnitScope scope = new UnitScope(RolePermissionScopeLevel.DEANERY,
                    uuid(row[2]), String.valueOf(row[3]), uuid(row[0]), String.valueOf(row[1]), null, null);
            requireConsistent(scope, dioceseId, null);
            return scope;
        }
        List<?> rows = entityManager.createNativeQuery("select id, name from diocese where id = :id")
                .setParameter("id", dioceseId).getResultList();
        if (rows.isEmpty()) {
            throw commonValidationHelper.notFound("Không tìm thấy giáo phận");
        }
        Object[] row = (Object[]) rows.get(0);
        return new UnitScope(RolePermissionScopeLevel.DIOCESE, uuid(row[0]), String.valueOf(row[1]), null, null, null, null);
    }

    private void requireConsistent(UnitScope scope, UUID dioceseId, UUID deaneryId) {
        if (dioceseId != null && !dioceseId.equals(scope.dioceseId())) {
            throw commonValidationHelper.validationError("Đơn vị đã chọn không thuộc giáo phận đã chọn");
        }
        if (deaneryId != null && !deaneryId.equals(scope.deaneryId())) {
            throw commonValidationHelper.validationError("Giáo xứ đã chọn không thuộc giáo hạt đã chọn");
        }
    }

    /** Tên các đơn vị theo id (giáo phận, giáo hạt, giáo xứ) để hiển thị trên danh sách. */
    public Map<UUID, String> unitNames(java.util.Collection<UUID> ids) {
        Map<UUID, String> names = new java.util.HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        for (String table : List.of("diocese", "deanery", "parish")) {
            List<?> rows = entityManager.createNativeQuery("select id, name from " + table + " where id in :ids")
                    .setParameter("ids", ids).getResultList();
            for (Object item : rows) {
                Object[] row = (Object[]) item;
                names.put(uuid(row[0]), String.valueOf(row[1]));
            }
        }
        return names;
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }
}
