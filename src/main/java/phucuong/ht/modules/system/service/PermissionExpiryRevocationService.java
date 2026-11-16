package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionExpiryRevocationService {

    static final String SYSTEM_ACTOR = "system:permission-expiry-revocation";

    private final RolePermissionRepository rolePermissionRepository;
    private final AccountPermissionOverrideRepository accountPermissionOverrideRepository;
    // Gửi thông báo cho vai trò / tài khoản bị thu hồi quyền hết hạn (tuỳ chọn để test đơn vị không cần).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.notification.service.PermissionGrantNotifier permissionGrantNotifier;
    // Ngoại lệ phân quyền vai trò theo đơn vị (GRANT có hạn): tuỳ chọn để các test đơn vị không cần.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.system.repository.RolePermissionUnitOverrideRepository unitOverrideRepository;

    @Transactional
    public PermissionExpiryRevocationSummary revokeExpiredPermissions() {
        return revokeExpiredPermissions(OffsetDateTime.now());
    }

    @Transactional
    PermissionExpiryRevocationSummary revokeExpiredPermissions(OffsetDateTime now) {
        // Lấy (và khóa) các quyền sắp hết hạn trước khi thu hồi hàng loạt để biết vai trò / tài khoản nào cần thông báo.
        java.util.Map<String, phucuong.ht.modules.system.model.entity.Role> rolesByCode = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<phucuong.ht.modules.system.model.entity.Permission>> expiredByRole = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<phucuong.ht.modules.system.model.entity.Permission>> expiredByAccount = new java.util.LinkedHashMap<>();
        if (permissionGrantNotifier != null) {
            for (var rolePermission : rolePermissionRepository.findExpiredAssignments(now)) {
                String roleCode = rolePermission.getRole().getRoleCode();
                rolesByCode.putIfAbsent(roleCode, rolePermission.getRole());
                expiredByRole.computeIfAbsent(roleCode, key -> new java.util.ArrayList<>()).add(rolePermission.getPermission());
            }
            for (var override : accountPermissionOverrideRepository.findExpiredOverrides(now)) {
                expiredByAccount.computeIfAbsent(override.getAccount().getUsername(), key -> new java.util.ArrayList<>()).add(override.getPermission());
            }
        }
        // Quyền cấp riêng cho vai trò tại một đơn vị hết hạn: mỗi (vai trò, đơn vị) nhận một thông báo, chỉ tới người giữ vai trò ở đơn vị đó.
        java.util.Map<String, java.util.List<phucuong.ht.modules.system.model.entity.Permission>> expiredByUnitRole = new java.util.LinkedHashMap<>();
        java.util.Map<String, phucuong.ht.modules.system.model.entity.RolePermissionUnitOverride> unitRoleKeys = new java.util.LinkedHashMap<>();
        if (unitOverrideRepository != null && permissionGrantNotifier != null) {
            for (var grant : unitOverrideRepository.findExpiredGrants(now)) {
                String key = grant.getRole().getRoleCode() + "|" + grant.getUnitId();
                unitRoleKeys.putIfAbsent(key, grant);
                expiredByUnitRole.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(grant.getPermission());
            }
        }
        int expiredRolePermissions = rolePermissionRepository.deactivateExpiredAssignments(now, now, SYSTEM_ACTOR);
        if (unitOverrideRepository != null) {
            expiredRolePermissions += unitOverrideRepository.deactivateExpiredGrants(now, now, SYSTEM_ACTOR);
        }
        int expiredAccountOverrides = accountPermissionOverrideRepository.deactivateExpiredOverrides(now, now, SYSTEM_ACTOR);
        // Mỗi vai trò / tài khoản nhận đúng một thông báo cho mỗi lần hệ thống thu hồi, kèm danh sách quyền hết hạn.
        if (permissionGrantNotifier != null) {
            expiredByRole.forEach((roleCode, permissions) -> permissionGrantNotifier.notifyRoleExpired(rolesByCode.get(roleCode), permissions));
            expiredByUnitRole.forEach((key, permissions) -> {
                var grant = unitRoleKeys.get(key);
                permissionGrantNotifier.notifyRoleExpired(grant.getRole(), permissions, grant.getUnitId(), null);
            });
            expiredByAccount.forEach(permissionGrantNotifier::notifyAccountExpired);
        }
        int totalRevoked = expiredRolePermissions + expiredAccountOverrides;

        if (totalRevoked > 0) {
            log.info(
                    "Automatically revoked {} expired permission records (rolePermissions={}, accountOverrides={})",
                    totalRevoked,
                    expiredRolePermissions,
                    expiredAccountOverrides
            );
        }

        return new PermissionExpiryRevocationSummary(expiredRolePermissions, expiredAccountOverrides);
    }

    public record PermissionExpiryRevocationSummary(
            int expiredRolePermissions,
            int expiredAccountOverrides
    ) {
        public int totalRevoked() {
            return expiredRolePermissions + expiredAccountOverrides;
        }
    }
}
