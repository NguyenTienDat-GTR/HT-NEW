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
 * (mặc định chung + GRANT ở U hoặc cấp trên) - EXCLUDE ở U hoặc cấp trên. Thu hồi riêng ở cấp trên luôn thắng:
 * cấp dưới không cấp lại được cho tới khi cấp đã thu hồi gỡ ra.
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

        List<RolePermissionUnitOverride> blockedAbove = overrideRepository
                .findActiveInChain(roleCode, code, scope.ancestors(), RolePermissionOverrideType.EXCLUDE);
        if (!blockedAbove.isEmpty()) {
            throw commonValidationHelper.conflict("Quyền đã bị thu hồi ở cấp " + levelName(blockedAbove.get(0).getScopeLevel())
                    + ", cần gỡ thu hồi ở cấp đó trước: " + code);
        }

        RolePermissionUnitOverride lifted = null;
        Optional<RolePermissionUnitOverride> ownExclude = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.EXCLUDE);
        if (ownExclude.isPresent() && isActive(ownExclude.get(), now)) {
            lifted = ownExclude.get();
            markRevoked(lifted, actor, now);
        }

        Optional<RolePermissionUnitOverride> ownGrant = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.GRANT);
        boolean grantHere = ownGrant.isPresent() && isActive(ownGrant.get(), now);
        boolean inheritedGrant = !overrideRepository
                .findActiveInChain(roleCode, code, scope.ancestors(), RolePermissionOverrideType.GRANT).isEmpty();
        if (defaultActive(roleCode, code, now) || inheritedGrant || grantHere) {
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

        boolean stillEffective = defaultActive(roleCode, code, now)
                || !overrideRepository.findActiveInChain(roleCode, code, scope.ancestors(), RolePermissionOverrideType.GRANT).isEmpty();
        boolean excluded = !overrideRepository.findActiveInChain(roleCode, code, scope.chain(), RolePermissionOverrideType.EXCLUDE).isEmpty();
        if (stillEffective && !excluded) {
            Optional<RolePermissionUnitOverride> ownExclude = overrideRepository.find(roleCode, code, scope.unitId(), RolePermissionOverrideType.EXCLUDE);
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
        String roleCode = role.getRoleCode();
        Set<String> codes = new LinkedHashSet<>(rolePermissionRepository.findPermissionCodesByRoleCode(roleCode));
        codes.addAll(overrideRepository.findActivePermissionCodesInChain(roleCode, scope.chain(), RolePermissionOverrideType.GRANT));
        codes.removeAll(overrideRepository.findActivePermissionCodesInChain(roleCode, scope.chain(), RolePermissionOverrideType.EXCLUDE));
        return codes;
    }

    /** Quyền đã bị thu hồi riêng ở cấp trên của đơn vị: không cấp lại được ở đơn vị này. */
    @Transactional(readOnly = true)
    public Set<String> blockedByAncestors(Role role, UnitScope scope) {
        return new LinkedHashSet<>(overrideRepository.findActivePermissionCodesInChain(
                role.getRoleCode(), scope.ancestors(), RolePermissionOverrideType.EXCLUDE));
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

    private String levelName(RolePermissionScopeLevel level) {
        return switch (level) {
            case DIOCESE -> "giáo phận";
            case DEANERY -> "giáo hạt";
            case PARISH -> "giáo xứ";
        };
    }
}
