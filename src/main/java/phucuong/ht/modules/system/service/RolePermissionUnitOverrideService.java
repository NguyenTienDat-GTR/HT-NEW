package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.entity.RolePermissionUnitOverride;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.enums.RolePermissionOverrideType;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.RolePermissionUnitOverrideRepository;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.UnitScope;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Máy trạng thái ngoại lệ phân quyền vai trò theo đơn vị. Quyền hiệu lực của vai trò tại đơn vị U:
 * ngoại lệ ở cấp hẹp nhất trong chuỗi giáo phận > giáo hạt > giáo xứ quyết định (GRANT = có, EXCLUDE = không), không có thì theo mặc định.
 * Cấp cụ thể hơn thắng: thu hồi cả giáo phận rồi vẫn phân lại riêng được cho một giáo hạt hoặc một giáo xứ.
 * Việc kiểm tra người thao tác nằm ở {@link RolePermissionUnitScopeResolver}, ở đây chỉ xử lý dữ liệu.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionUnitOverrideService {

    private final RolePermissionUnitOverrideRepository overrideRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CommonValidationHelper commonValidationHelper;

    /** Kết quả của một lần gán / thu hồi: dòng ngoại lệ bị thay đổi để trả về cho người gọi. */
    public record Change(RolePermissionUnitOverride entity) {
    }

    /** Cấp (hoặc khôi phục) quyền cho vai trò tại đơn vị. */
    @Transactional
    public Change assign(Role role, Permission permission, UnitScope scope, String conditions,
                         OffsetDateTime assignedAt, OffsetDateTime expiresAt, String actor) {
        String roleCode = role.getRoleCode();
        String code = permission.getPermissionCode();
        OffsetDateTime now = OffsetDateTime.now();

        RolePermissionUnitOverride lifted = null;
        Optional<RolePermissionUnitOverride> ownExclude = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.EXCLUDE);
        if (ownExclude.isPresent() && isActive(ownExclude.get(), now)) {
            lifted = ownExclude.get();
            markRevoked(lifted, actor, now);
        }

        Optional<RolePermissionUnitOverride> ownGrant = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.GRANT);
        boolean grantHere = ownGrant.isPresent() && isActive(ownGrant.get(), now);
        // Cấp cụ thể hơn thắng: quyền bị thu hồi ở cấp trên vẫn phân lại riêng được cho đơn vị này.
        if (grantHere || effectiveFromAbove(roleCode, code, scope, now)) {
            if (lifted == null) {
                throw commonValidationHelper.conflict("Vai trò đã có quyền này tại " + scope.label() + ": " + code);
            }
            return new Change(lifted);
        }

        RolePermissionUnitOverride grant = ownGrant.orElseGet(() -> newRow(role, permission, scope, RolePermissionOverrideType.GRANT, actor));
        reissue(grant, actor, assignedAt == null ? now : assignedAt, now);
        grant.setConditions(commonValidationHelper.normalizeOptionalText(conditions));
        grant.setExpiresAt(expiresAt);
        if (ownGrant.isEmpty()) {
            overrideRepository.save(grant);
        }
        return new Change(grant);
    }

    /** Thu hồi quyền của vai trò tại đơn vị: huỷ quyền cấp riêng nếu có, và thu hồi riêng nếu quyền vẫn còn từ mặc định / cấp trên. */
    @Transactional
    public Change revoke(Role role, Permission permission, UnitScope scope, String actor) {
        String roleCode = role.getRoleCode();
        String code = permission.getPermissionCode();
        OffsetDateTime now = OffsetDateTime.now();
        RolePermissionUnitOverride changed = null;

        Optional<RolePermissionUnitOverride> ownGrant = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.GRANT);
        if (ownGrant.isPresent() && isActive(ownGrant.get(), now)) {
            markRevoked(ownGrant.get(), actor, now);
            changed = ownGrant.get();
        }

        Optional<RolePermissionUnitOverride> ownExclude = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.EXCLUDE);
        boolean excludedHere = ownExclude.isPresent() && isActive(ownExclude.get(), now);
        if (!excludedHere && effectiveFromAbove(roleCode, code, scope, now)) {
            RolePermissionUnitOverride exclude = ownExclude.orElseGet(() -> newRow(role, permission, scope, RolePermissionOverrideType.EXCLUDE, actor));
            reissue(exclude, actor, now, now);
            exclude.setConditions(null);
            exclude.setExpiresAt(null);
            if (ownExclude.isEmpty()) {
                overrideRepository.save(exclude);
            }
            changed = exclude;
        }

        if (changed == null) {
            throw commonValidationHelper.notFound("Vai trò chưa có quyền này tại " + scope.label() + ": " + code);
        }
        return new Change(changed);
    }

    /** Mã quyền hiệu lực của vai trò tại đơn vị (đã tính cả cấp trên). */
    @Transactional(readOnly = true)
    public Set<String> effectiveCodes(Role role, UnitScope scope) {
        Set<String> codes = new LinkedHashSet<>(rolePermissionRepository.findPermissionCodesByRoleCode(role.getRoleCode()));
        // Với mỗi quyền, ngoại lệ ở cấp hẹp nhất trong chuỗi quyết định: cấp riêng thì có, thu hồi riêng thì không.
        java.util.Map<String, RolePermissionUnitOverride> winners = new java.util.HashMap<>();
        for (RolePermissionUnitOverride override : overrideRepository.findActiveByRoleInChain(role.getRoleCode(), scope.chain())) {
            winners.merge(override.getPermission().getPermissionCode(), override,
                    (a, b) -> a.getScopeLevel().ordinal() >= b.getScopeLevel().ordinal() ? a : b);
        }
        winners.forEach((code, override) -> {
            if (override.getOverrideType() == RolePermissionOverrideType.GRANT) {
                codes.add(code);
            } else {
                codes.remove(code);
            }
        });
        return codes;
    }

    /**
     * Quyền của vai trò tại đơn vị nếu bỏ qua ngoại lệ đặt ngay tại đơn vị đó: ngoại lệ ở cấp hẹp nhất trong các cấp trên quyết định
     * (cấp riêng thì có, thu hồi riêng thì không), không có thì theo mặc định.
     */
    private boolean effectiveFromAbove(String roleCode, String code, UnitScope scope, OffsetDateTime now) {
        RolePermissionUnitOverride winner = null;
        for (RolePermissionOverrideType type : RolePermissionOverrideType.values()) {
            for (RolePermissionUnitOverride candidate : overrideRepository.findActiveInChain(roleCode, code, scope.ancestors(), type)) {
                if (winner == null || candidate.getScopeLevel().ordinal() > winner.getScopeLevel().ordinal()) {
                    winner = candidate;
                }
            }
        }
        if (winner != null) {
            return winner.getOverrideType() == RolePermissionOverrideType.GRANT;
        }
        return defaultActive(roleCode, code, now);
    }

    private boolean defaultActive(String roleCode, String permissionCode, OffsetDateTime now) {
        Optional<RolePermission> row = rolePermissionRepository.findByRoleCodeAndPermissionCode(roleCode, permissionCode);
        return row.isPresent()
                && Boolean.TRUE.equals(row.get().getStatus())
                && !PermissionValiditySupport.isPermanentlyRevoked(row.get().getRevokedAt(), row.get().getExpiresAt(), now);
    }

    private boolean isActive(RolePermissionUnitOverride row, OffsetDateTime now) {
        return Boolean.TRUE.equals(row.getStatus())
                && !PermissionValiditySupport.isPermanentlyRevoked(row.getRevokedAt(), row.getExpiresAt(), now);
    }

    private void markRevoked(RolePermissionUnitOverride row, String actor, OffsetDateTime now) {
        row.setRevokedAt(now);
        row.setRevokedBy(actor);
        row.setRevocationReason(PermissionRevocationReason.MANUAL);
        row.setUpdatedAt(now);
        row.setUpdatedBy(actor);
    }

    /** Dòng đã thu hồi/hết hạn không bật lại được: cấp mới thay thế chính dòng đó (giữ ràng buộc duy nhất). */
    private void reissue(RolePermissionUnitOverride row, String actor, OffsetDateTime assignedAt, OffsetDateTime now) {
        row.setRevokedAt(null);
        row.setRevokedBy(null);
        row.setRevocationReason(null);
        row.setAssignedBy(actor);
        row.setAssignedAt(assignedAt);
        row.setStatus(true);
        row.setUpdatedAt(now);
        row.setUpdatedBy(actor);
    }

    private RolePermissionUnitOverride newRow(Role role, Permission permission, UnitScope scope, RolePermissionOverrideType type, String actor) {
        RolePermissionUnitOverride row = new RolePermissionUnitOverride();
        row.setRole(role);
        row.setPermission(permission);
        row.setOverrideType(type);
        row.setScopeLevel(scope.level());
        row.setDioceseId(scope.level() == RolePermissionScopeLevel.DIOCESE ? scope.dioceseId() : null);
        row.setDeaneryId(scope.level() == RolePermissionScopeLevel.DEANERY ? scope.deaneryId() : null);
        row.setParishId(scope.level() == RolePermissionScopeLevel.PARISH ? scope.parishId() : null);
        row.setCreatedBy(actor);
        row.setStatus(true);
        return row;
    }
}
