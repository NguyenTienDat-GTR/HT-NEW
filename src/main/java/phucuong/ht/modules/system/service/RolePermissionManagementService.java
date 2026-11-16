package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionRevokeRequest;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.model.entity.RolePermissionListing;
import phucuong.ht.modules.system.model.entity.RolePermissionUnitOverride;
import phucuong.ht.modules.system.model.enums.PermissionValidity;
import phucuong.ht.modules.system.model.enums.RolePermissionEntryType;
import phucuong.ht.modules.system.model.enums.RolePermissionOverrideType;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;
import phucuong.ht.modules.system.repository.RolePermissionListingRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.RolePermissionUnitOverrideRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.Actor;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.ActorTier;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.UnitScope;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RolePermissionManagementService {

    private static final Map<String, String> REPOSITORY_FIELD_ALIASES = Map.of(
            "roleCode", "role.roleCode",
            "roleName", "role.roleName",
            "permissionCode", "permission.permissionCode",
            "permissionName", "permission.permissionName"
    );
    private static final Set<String> REPOSITORY_FIELDS = Set.of(
            "id",
            "role.roleCode",
            "role.roleName",
            "permission.permissionCode",
            "permission.permissionName",
            "permission.module.moduleCode",
            "permission.resource.resourceCode",
            "permission.action.actionCode",
            "permission.scope.scopeCode",
            "entryType",
            "scopeLevel",
            "conditions",
            "assignedBy",
            "assignedAt",
            "expiresAt",
            "status",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy"
    );

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    private final RolePermissionUnitScopeResolver unitScopeResolver;
    private final RolePermissionUnitOverrideService unitOverrideService;
    private final RolePermissionUnitOverrideRepository unitOverrideRepository;
    private final RolePermissionListingRepository listingRepository;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;
    @Autowired(required = false)
    private phucuong.ht.modules.notification.service.PermissionGrantNotifier permissionGrantNotifier;

    @Transactional
    public RolePermissionResponse create(RolePermissionCreateRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        Permission permission = systemPermissionRepository.findById(request.getPermissionCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + request.getPermissionCode()));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }
        if (!Boolean.TRUE.equals(permission.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động");
        }

        List<UnitScope> units = unitScopeResolver.resolveAll(authentication, role,
                ids(request.getDioceseId(), request.getDioceseIds()), ids(request.getDeaneryId(), request.getDeaneryIds()),
                ids(request.getParishId(), request.getParishIds()), false);
        if (!units.isEmpty()) {
            return assignForUnits(role, units, List.of(request.getPermissionCode()), request.getConditions(),
                    request.getAssignedAt(), request.getExpiresAt(), authentication, "ROLE_PERMISSION_CREATED").get(0);
        }

        authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        RolePermission existing = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), request.getPermissionCode()).orElse(null);
        if (existing != null) {
            if (!PermissionValiditySupport.isPermanentlyRevoked(existing.getRevokedAt(), existing.getExpiresAt(), OffsetDateTime.now())) {
                throw commonValidationHelper.conflict("Phân quyền cho vai trò đã tồn tại");
            }
            // Quyền đã thu hồi/hết hạn không bật lại được: phân quyền mới thay thế bản ghi cũ.
            OffsetDateTime assignedAt = request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt();
            existing.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
            existing.setExpiresAt(request.getExpiresAt());
            reissue(existing, actor, assignedAt);
            notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_CREATED");
            notifyRoleGranted(role, List.of(permission), actor);
            return toResponse(existing);
        }

        RolePermission rolePermission = rolePermissionRepository.create(request, actor);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt());
        notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_CREATED");
        notifyRoleGranted(role, List.of(permission), actor);
        return toResponse(rolePermission);
    }

    @Transactional
    public List<RolePermissionResponse> bulkAssign(RolePermissionBulkRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }

        List<UnitScope> units = unitScopeResolver.resolveAll(authentication, role,
                ids(request.getDioceseId(), request.getDioceseIds()), ids(request.getDeaneryId(), request.getDeaneryIds()),
                ids(request.getParishId(), request.getParishIds()), false);
        if (!units.isEmpty()) {
            return assignForUnits(role, units, new ArrayList<>(new LinkedHashSet<>(request.getPermissionCodes())),
                    request.getConditions(), request.getAssignedAt(), request.getExpiresAt(), authentication, "ROLE_PERMISSION_BULK_ASSIGNED");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        OffsetDateTime assignedAt = request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt();
        OffsetDateTime now = OffsetDateTime.now();
        List<RolePermissionResponse> responses = new ArrayList<>();
        List<Permission> grantedPermissions = new ArrayList<>();

        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            Permission permission = systemPermissionRepository.findById(permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
            if (!Boolean.TRUE.equals(permission.getStatus())) {
                throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động: " + permissionCode);
            }
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);

            RolePermission rolePermission = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), permissionCode)
                    .orElseGet(() -> createRolePermission(role, permission, actor, assignedAt));
            if (PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), now)) {
                reissue(rolePermission, actor, assignedAt);
            }
            rolePermission.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
            rolePermission.setExpiresAt(request.getExpiresAt());
            rolePermission.setStatus(true);
            rolePermission.setUpdatedAt(now);
            rolePermission.setUpdatedBy(actor);
            responses.add(toResponse(rolePermission));
            grantedPermissions.add(permission);
        }

        notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_BULK_ASSIGNED");
        notifyRoleGranted(role, grantedPermissions, actor);
        return responses;
    }

    /** Thu hồi nhiều quyền đã gán cho vai trò: đánh dấu thu hồi vĩnh viễn, giữ lại bản ghi (muốn cấp lại phải phân quyền mới). */
    @Transactional
    public int bulkRevoke(RolePermissionRevokeRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        List<UnitScope> units = unitScopeResolver.resolveAll(authentication, role,
                ids(request.getDioceseId(), request.getDioceseIds()), ids(request.getDeaneryId(), request.getDeaneryIds()),
                ids(request.getParishId(), request.getParishIds()), true);
        if (!units.isEmpty()) {
            return revokeForUnits(role, units, new ArrayList<>(new LinkedHashSet<>(request.getPermissionCodes())), authentication);
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi phân quyền của vai trò");
        int revoked = 0;
        List<Permission> revokedPermissions = new java.util.ArrayList<>();
        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            RolePermission rolePermission = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Vai trò chưa được gán quyền: " + permissionCode));
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, rolePermission.getPermission(), SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE);
            authorizationHelper.requireRevocableOrigin(authentication, rolePermission.getAssignedBy(), rolePermission.getCreatedBy());
            authorizationHelper.requireNotOwnRole(authentication, role);
            OffsetDateTime now = OffsetDateTime.now();
            if (PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), now)) {
                throw commonValidationHelper.conflict("Quyền đã bị thu hồi hoặc hết hạn: " + permissionCode);
            }
            rolePermission.setRevokedAt(now);
            rolePermission.setRevokedBy(actor);
            rolePermission.setRevocationReason(PermissionRevocationReason.MANUAL);
            rolePermission.setUpdatedAt(now);
            rolePermission.setUpdatedBy(actor);
            revokedPermissions.add(rolePermission.getPermission());
            revoked++;
        }
        notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_BULK_REVOKED");
        // Một request thu hồi gửi đúng một thông báo cho các tài khoản đang giữ vai trò, kèm danh sách quyền bị thu hồi.
        if (permissionGrantNotifier != null) {
            permissionGrantNotifier.notifyRoleRevoked(role, revokedPermissions, actor);
        }
        return revoked;
    }

    /** Quyền đã gán cho vai trò mà tài khoản hiện tại được phép thu hồi. */
    @Transactional(readOnly = true)
    public List<PermissionResponse> findRevocablePermissions(String roleCode, List<java.util.UUID> dioceseIds, List<java.util.UUID> deaneryIds,
                                                             List<java.util.UUID> parishIds, Authentication authentication) {
        Role role = roleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        List<UnitScope> units = unitScopeResolver.resolveAll(authentication, role, dioceseIds, deaneryIds, parishIds, true);
        if (!units.isEmpty()) {
            Actor actor = unitScopeResolver.actor(authentication, true);
            // Chọn nhiều nơi: liệt kê quyền đang có ở ít nhất một nơi (nơi chưa có sẽ được bỏ qua khi thu hồi).
            Set<String> effective = new LinkedHashSet<>();
            for (UnitScope unit : units) {
                effective.addAll(unitOverrideService.effectiveCodes(role, unit));
            }
            return effective.stream()
                    .map(systemPermissionRepository::findById)
                    .flatMap(java.util.Optional::stream)
                    .filter(permission -> unitScopeResolver.permissionAllowed(actor, permission))
                    .map(this::toPermissionResponse)
                    .toList();
        }
        return rolePermissionRepository.findPermissionCodesByRoleCode(roleCode).stream()
                .map(systemPermissionRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(permission -> canRevokePermission(authentication, role, permission))
                .filter(permission -> rolePermissionRepository.findByRoleCodeAndPermissionCode(roleCode, permission.getPermissionCode())
                        .map(assignment -> authorizationHelper.canRevokeRolePermission(authentication, role, permission, assignment.getAssignedBy(), assignment.getCreatedBy()))
                        .orElse(false))
                .map(this::toPermissionResponse)
                .toList();
    }

    private boolean canRevokePermission(Authentication authentication, Role role, Permission permission) {
        try {
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Page<RolePermissionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        UnitScope selected = selectedUnit(request);
        String validityFilter = validityFilter(request);
        Set<String> excluded = new java.util.HashSet<>();
        if (selected != null) {
            // Quyền mặc định bị ẩn khi ngoại lệ ở cấp hẹp nhất trong chuỗi của đơn vị đang lọc là thu hồi riêng.
            Map<String, RolePermissionUnitOverride> winners = new java.util.HashMap<>();
            for (RolePermissionUnitOverride override : unitOverrideRepository.findActiveInUnits(selected.chain())) {
                winners.merge(override.getRole().getRoleCode() + "|" + override.getPermission().getPermissionCode(), override,
                        (a, b) -> a.getScopeLevel().ordinal() >= b.getScopeLevel().ordinal() ? a : b);
            }
            winners.forEach((key, override) -> {
                if (override.getOverrideType() == RolePermissionOverrideType.EXCLUDE) {
                    excluded.add(key);
                }
            });
        }
        Actor viewer = unitScopeResolver.actor(authentication, false);
        List<RolePermissionListing> manageable = loadAllMatching(request).stream()
                .filter(row -> authorizationHelper.canReadRolePermission(authentication, row.getRole(), row.getPermission()))
                .filter(row -> visibleToViewer(row, viewer))
                .filter(row -> selected == null || matchesSelectedUnit(row, selected, excluded))
                .filter(row -> matchesValidity(row, validityFilter))
                .toList();
        return page(request, manageable, authentication);
    }

    /**
     * Bộ lọc tình trạng quyền (khóa validity): ACTIVE = còn hiệu lực; INACTIVE = hết hiệu lực (đã thu hồi, hết hạn hoặc quyền bị vô hiệu hóa);
     * hoặc đúng một giá trị của {@link PermissionValidity}. Tình trạng được tính ra nên không lọc được ở tầng truy vấn.
     */
    private String validityFilter(BaseSearchRequest request) {
        Map<String, Object> filters = request == null ? null : request.getFilters();
        Object value = filters == null ? null : filters.get("validity");
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String text = String.valueOf(value).trim().toUpperCase();
        if (!text.equals("ACTIVE") && !text.equals("INACTIVE")) {
            try {
                PermissionValidity.valueOf(text);
            } catch (IllegalArgumentException ex) {
                throw commonValidationHelper.validationError("Tình trạng quyền lọc không hợp lệ");
            }
        }
        return text;
    }

    private boolean matchesValidity(RolePermissionListing row, String filter) {
        if (filter == null) {
            return true;
        }
        PermissionValidity validity = row.getEntryType() == RolePermissionEntryType.UNIT_EXCLUDE
                ? PermissionValidity.REVOKED
                : PermissionValiditySupport.validity(row.getRevokedAt(), row.getRevocationReason(), row.getExpiresAt(), row.getPermission(), OffsetDateTime.now());
        return switch (filter) {
            case "ACTIVE" -> validity == PermissionValidity.ACTIVE;
            case "INACTIVE" -> validity != PermissionValidity.ACTIVE;
            default -> validity.name().equals(filter);
        };
    }

    /** Đơn vị người dùng đang lọc trên danh sách (bộ lọc dioceseId / deaneryId / parishId), hoặc null khi không lọc. */
    private UnitScope selectedUnit(BaseSearchRequest request) {
        Map<String, Object> filters = request == null ? null : request.getFilters();
        if (filters == null) {
            return null;
        }
        java.util.UUID diocese = uuidFilter(filters.get("dioceseId"));
        java.util.UUID deanery = uuidFilter(filters.get("deaneryId"));
        java.util.UUID parish = uuidFilter(filters.get("parishId"));
        if (diocese == null && deanery == null && parish == null) {
            return null;
        }
        return unitScopeResolver.load(diocese, deanery, parish);
    }

    private java.util.UUID uuidFilter(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            throw commonValidationHelper.validationError("Mã đơn vị lọc không hợp lệ");
        }
    }

    /** Dòng ngoại lệ chỉ hiện với superadmin và với admin giáo phận / giáo hạt trong chuỗi đơn vị của họ; dòng mặc định luôn qua. */
    private boolean visibleToViewer(RolePermissionListing row, Actor viewer) {
        if (row.getEntryType() == null || row.getEntryType() == RolePermissionEntryType.DEFAULT || viewer.tier() == ActorTier.SUPER_ADMIN) {
            return true;
        }
        return switch (viewer.tier()) {
            case DIOCESE -> viewer.dioceseId() != null && viewer.dioceseId().equals(row.getDioceseId());
            case DEANERY -> viewer.deaneryId() != null && (viewer.deaneryId().equals(row.getDeaneryId())
                    || (row.getScopeLevel() == RolePermissionScopeLevel.DIOCESE && viewer.dioceseId() != null
                    && viewer.dioceseId().equals(row.getDioceseId())));
            default -> false;
        };
    }

    /**
     * Khi lọc theo một đơn vị U: dòng mặc định hiện trừ khi bị thu hồi riêng ở U hoặc cấp trên;
     * dòng ngoại lệ hiện nếu thuộc U, cấp trên của U hoặc đơn vị cấp dưới của U.
     */
    private boolean matchesSelectedUnit(RolePermissionListing row, UnitScope selected, Set<String> excluded) {
        if (row.getEntryType() == null || row.getEntryType() == RolePermissionEntryType.DEFAULT) {
            return !excluded.contains(row.getRole().getRoleCode() + "|" + row.getPermission().getPermissionCode());
        }
        java.util.UUID unitId = selected.unitId();
        if (unitId.equals(row.getDioceseId()) || unitId.equals(row.getDeaneryId()) || unitId.equals(row.getParishId())) {
            return true;
        }
        java.util.UUID own = switch (row.getScopeLevel()) {
            case DIOCESE -> row.getDioceseId();
            case DEANERY -> row.getDeaneryId();
            case PARISH -> row.getParishId();
        };
        return own != null && selected.chain().contains(own);
    }

    /** Vai trò mà người dùng hiện tại được phép phân quyền / thu hồi quyền (action = assign | revoke); đã loại vai trò cấp cao hơn. */
    @Transactional(readOnly = true)
    public List<RoleResponse> findManageableRoles(String action, Authentication authentication) {
        String authority = "revoke".equalsIgnoreCase(action)
                ? SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE
                : SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE;
        boolean assign = !"revoke".equalsIgnoreCase(action);
        Actor unitActor = unitScopeResolver.actor(authentication, !assign);
        return roleRepository.findAll().stream()
                .filter(role -> !assign || Boolean.TRUE.equals(role.getStatus()))
                // Admin giáo phận / giáo hạt thêm các vai trò admin cấp dưới mà họ được chỉnh quyền riêng theo đơn vị.
                .filter(role -> authorizationHelper.canManageRolePermissionRole(authentication, role, authority)
                        || (unitActor.tier() != ActorTier.SUPER_ADMIN && unitScopeResolver.roleEligible(unitActor, role)))
                .sorted(java.util.Comparator.comparing(Role::getDisplayOrder, java.util.Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Role::getRoleCode))
                .map(role -> RoleResponse.builder()
                        .roleCode(role.getRoleCode())
                        .roleName(role.getRoleName())
                        .description(role.getDescription())
                        .isSystem(role.getIsSystem())
                        .displayOrder(role.getDisplayOrder())
                        .status(role.getStatus())
                        .createdAt(role.getCreatedAt())
                        .createdBy(role.getCreatedBy())
                        .createdByName(auditActorNameResolver.resolve(role.getCreatedBy()))
                        .updatedAt(role.getUpdatedAt())
                        .updatedBy(role.getUpdatedBy())
                        .updatedByName(auditActorNameResolver.resolve(role.getUpdatedBy()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAssignablePermissions(String roleCode, List<java.util.UUID> dioceseIds, List<java.util.UUID> deaneryIds,
                                                              List<java.util.UUID> parishIds, Authentication authentication) {
        Role role = roleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }
        List<UnitScope> units = unitScopeResolver.resolveAll(authentication, role, dioceseIds, deaneryIds, parishIds, false);
        if (!units.isEmpty()) {
            Actor actor = unitScopeResolver.actor(authentication, false);
            // Quyền đã thu hồi riêng ở cấp trên vẫn nằm trong danh sách này để phân lại riêng cho đơn vị đang chọn.
            // Chọn nhiều nơi: liệt kê quyền còn thiếu ở ít nhất một nơi (nơi đã có sẽ được bỏ qua khi phân quyền).
            List<Set<String>> effectivePerUnit = units.stream().map(unit -> unitOverrideService.effectiveCodes(role, unit)).toList();
            return systemPermissionRepository.findActivePermissions().stream()
                    .filter(permission -> effectivePerUnit.stream().anyMatch(codes -> !codes.contains(permission.getPermissionCode())))
                    .filter(permission -> unitScopeResolver.permissionAllowed(actor, permission))
                    .map(this::toPermissionResponse)
                    .toList();
        }
        Set<String> assignedPermissionCodes = Set.copyOf(rolePermissionRepository.findPermissionCodesByRoleCode(roleCode));
        return systemPermissionRepository.findActivePermissions().stream()
                .filter(permission -> !assignedPermissionCodes.contains(permission.getPermissionCode()))
                .filter(permission -> canAssignPermission(authentication, role, permission))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RolePermissionResponse findById(String id, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id).orElse(null);
        if (rolePermission == null) {
            RolePermissionUnitOverride override = unitOverrideRepository.findById(id)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
            authorizationHelper.requireRolePermissionReadAccess(authentication, override.getRole(), override.getPermission());
            RolePermissionListing row = toListingRow(override, scopeOf(override));
            if (!visibleToViewer(row, unitScopeResolver.actor(authentication, false))) {
                throw commonValidationHelper.forbidden("Không có quyền xem phân quyền của đơn vị này");
            }
            return toResponse(row, authentication, unitScopeResolver.actor(authentication, true), namesOf(List.of(row)));
        }
        authorizationHelper.requireRolePermissionReadAccess(authentication, rolePermission.getRole(), rolePermission.getPermission());
        return toResponse(rolePermission, authentication);
    }

    @Transactional
    public RolePermissionResponse update(String id, RolePermissionUpdateRequest request, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id).orElse(null);
        if (rolePermission == null) {
            return updateOverride(id, request, authentication);
        }
        authorizationHelper.requireRolePermissionWriteAccess(
                authentication,
                rolePermission.getRole(),
                rolePermission.getPermission(),
                SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE
        );
        requireNotRevoked(rolePermission);

        if (request.getConditions() != null) {
            rolePermission.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
        }
        if (request.getExpiresAt() != null) {
            rolePermission.setExpiresAt(request.getExpiresAt());
        }
        rolePermission.setUpdatedAt(OffsetDateTime.now());
        rolePermission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật phân quyền cho vai trò"));
        notifyRolePermissionChanged(rolePermission.getRole().getRoleCode(), "ROLE_PERMISSION_UPDATED");
        return toResponse(rolePermission);
    }


    private void requireNotRevoked(RolePermission rolePermission) {
        if (PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), OffsetDateTime.now())) {
            throw commonValidationHelper.conflict("Phân quyền đã bị thu hồi hoặc hết hạn, không thể thay đổi; hãy tạo phân quyền mới");
        }
    }

    private void reissue(RolePermission rolePermission, String actor, OffsetDateTime assignedAt) {
        rolePermission.setRevokedAt(null);
        rolePermission.setRevokedBy(null);
        rolePermission.setRevocationReason(null);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(assignedAt);
        rolePermission.setStatus(true);
        rolePermission.setUpdatedAt(OffsetDateTime.now());
        rolePermission.setUpdatedBy(actor);
    }

    // Một request phân quyền gửi đúng một thông báo cho các tài khoản đang giữ vai trò, kèm danh sách quyền vừa phân.
    private void notifyRoleGranted(Role role, List<Permission> permissions, String actor) {
        if (permissionGrantNotifier != null) {
            permissionGrantNotifier.notifyRoleGranted(role, permissions, actor);
        }
    }

    private void notifyRolePermissionChanged(String roleCode, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyRoleChanged(roleCode, reason);
        }
    }





    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(normalizeFieldPaths(source.getSearchFields()));
        copy.setFilters(normalizeFilters(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(toRepositoryField(source.getSortBy()));
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private List<String> normalizeFieldPaths(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream()
                .map(this::toRepositoryField)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private LinkedHashMap<String, Object> normalizeFilters(java.util.Map<String, Object> filters) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (filters == null || filters.isEmpty()) {
            return normalized;
        }
        filters.forEach((key, value) -> {
            String repositoryField = toRepositoryField(key);
            if (repositoryField != null) {
                normalized.put(repositoryField, value);
            }
        });
        return normalized;
    }

    private String toRepositoryField(String field) {
        if (field == null) {
            return null;
        }
        String normalized = field.trim();
        String repositoryField = REPOSITORY_FIELD_ALIASES.getOrDefault(normalized, normalized);
        return REPOSITORY_FIELDS.contains(repositoryField) ? repositoryField : null;
    }

    private List<RolePermissionListing> loadAllMatching(BaseSearchRequest request) {
        List<RolePermissionListing> matches = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = copyRequest(request);
            batch.setPage(page);
            batch.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
            Page<RolePermissionListing> result = listingRepository.findAll(batch);
            matches.addAll(result.getContent());
            if (!result.hasNext()) {
                return matches;
            }
            page++;
        }
    }

    private Page<RolePermissionResponse> page(BaseSearchRequest request, List<RolePermissionListing> rolePermissions, Authentication authentication) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, rolePermissions.size());
        int end = Math.min(start + size, rolePermissions.size());
        List<RolePermissionListing> slice = rolePermissions.subList(start, end);
        Map<java.util.UUID, String> names = namesOf(slice);
        Actor revoker = unitScopeResolver.actor(authentication, true);
        return new PageImpl<>(slice.stream().map(item -> toResponse(item, authentication, revoker, names)).toList(), PageRequest.of(page, size), rolePermissions.size());
    }

    /** Tên đơn vị của các dòng ngoại lệ trên trang (một lượt truy vấn cho cả trang). */
    private Map<java.util.UUID, String> namesOf(List<RolePermissionListing> rows) {
        Set<java.util.UUID> ids = new LinkedHashSet<>();
        for (RolePermissionListing row : rows) {
            if (row.getDioceseId() != null) {
                ids.add(row.getDioceseId());
            }
            if (row.getDeaneryId() != null) {
                ids.add(row.getDeaneryId());
            }
            if (row.getParishId() != null) {
                ids.add(row.getParishId());
            }
        }
        return unitScopeResolver.unitNames(ids);
    }

    private RolePermissionResponse toResponse(RolePermissionListing row, Authentication authentication, Actor revoker,
                                              Map<java.util.UUID, String> names) {
        OffsetDateTime now = OffsetDateTime.now();
        RolePermissionEntryType type = row.getEntryType() == null ? RolePermissionEntryType.DEFAULT : row.getEntryType();
        boolean revoked = row.getRevokedAt() != null;
        boolean permanentlyRevoked = PermissionValiditySupport.isPermanentlyRevoked(row.getRevokedAt(), row.getExpiresAt(), now);
        boolean revocable = switch (type) {
            case DEFAULT -> !permanentlyRevoked && authorizationHelper.canRevokeRolePermission(authentication, row.getRole(),
                    row.getPermission(), row.getAssignedBy(), row.getCreatedBy());
            case UNIT_GRANT -> !permanentlyRevoked && unitScopeResolver.canManageRow(revoker, row.getRole(), row.getPermission(),
                    row.getScopeLevel(), row.getDioceseId(), row.getDeaneryId());
            case UNIT_EXCLUDE -> false;
        };
        PermissionValidity validity = type == RolePermissionEntryType.UNIT_EXCLUDE
                ? PermissionValidity.REVOKED
                : PermissionValiditySupport.validity(row.getRevokedAt(), row.getRevocationReason(), row.getExpiresAt(), row.getPermission(), now);
        // Thu hồi riêng theo đơn vị hiển thị như một dòng đã thu hồi, người thu hồi là người đặt ngoại lệ.
        OffsetDateTime revokedAt = type == RolePermissionEntryType.UNIT_EXCLUDE ? row.getAssignedAt() : row.getRevokedAt();
        String revokedBy = type == RolePermissionEntryType.UNIT_EXCLUDE ? row.getAssignedBy() : row.getRevokedBy();
        phucuong.ht.modules.system.model.enums.PermissionRevocationReason reason = type == RolePermissionEntryType.UNIT_EXCLUDE
                ? PermissionRevocationReason.MANUAL : row.getRevocationReason();
        String dioceseName = row.getDioceseId() == null ? null : names.get(row.getDioceseId());
        String deaneryName = row.getDeaneryId() == null ? null : names.get(row.getDeaneryId());
        String parishName = row.getParishId() == null ? null : names.get(row.getParishId());
        return RolePermissionResponse.builder()
                .id(row.getId())
                .entryType(type)
                .scopeLevel(row.getScopeLevel())
                .dioceseId(row.getDioceseId()).dioceseName(dioceseName)
                .deaneryId(row.getDeaneryId()).deaneryName(deaneryName)
                .parishId(row.getParishId()).parishName(parishName)
                .scopeName(scopeName(row.getScopeLevel(), dioceseName, deaneryName, parishName))
                .roleCode(row.getRole() == null ? null : row.getRole().getRoleCode())
                .roleName(row.getRole() == null ? null : row.getRole().getRoleName())
                .permissionCode(row.getPermission() == null ? null : row.getPermission().getPermissionCode())
                .permissionName(row.getPermission() == null ? null : row.getPermission().getPermissionName())
                .conditions(row.getConditions())
                .assignedBy(row.getAssignedBy())
                .assignedByName(auditActorNameResolver.resolve(row.getAssignedBy()))
                .assignedAt(row.getAssignedAt())
                .expiresAt(row.getExpiresAt())
                .status(row.getStatus())
                .validity(validity)
                .revocable(revocable)
                .revocationReason(reason)
                .revokedAt(revokedAt)
                .revokedBy(revokedBy)
                .revokedByName(auditActorNameResolver.resolve(revokedBy))
                .createdAt(row.getCreatedAt())
                .createdBy(row.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(row.getCreatedBy()))
                .updatedAt(row.getUpdatedAt())
                .updatedBy(row.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(row.getUpdatedBy()))
                .build();
    }

    private String scopeName(RolePermissionScopeLevel level, String dioceseName, String deaneryName, String parishName) {
        if (level == null) {
            return "Tất cả giáo phận";
        }
        return switch (level) {
            case DIOCESE -> "Giáo phận " + dioceseName;
            case DEANERY -> "Giáo hạt " + deaneryName;
            case PARISH -> "Giáo xứ " + parishName;
        };
    }

    /** Dòng ngoại lệ dưới dạng dòng danh sách (không lưu), để dùng chung bộ dựng phản hồi. */
    private RolePermissionListing toListingRow(RolePermissionUnitOverride override, UnitScope scope) {
        RolePermissionListing row = new RolePermissionListing();
        row.setId(override.getId());
        row.setRole(override.getRole());
        row.setPermission(override.getPermission());
        row.setEntryType(override.getOverrideType() == RolePermissionOverrideType.GRANT
                ? RolePermissionEntryType.UNIT_GRANT : RolePermissionEntryType.UNIT_EXCLUDE);
        row.setScopeLevel(override.getScopeLevel());
        row.setDioceseId(scope.dioceseId());
        row.setDeaneryId(scope.deaneryId());
        row.setParishId(scope.parishId());
        row.setConditions(override.getConditions());
        row.setAssignedBy(override.getAssignedBy());
        row.setAssignedAt(override.getAssignedAt());
        row.setExpiresAt(override.getExpiresAt());
        row.setRevokedAt(override.getRevokedAt());
        row.setRevokedBy(override.getRevokedBy());
        row.setRevocationReason(override.getRevocationReason());
        row.setStatus(override.getStatus());
        row.setCreatedAt(override.getCreatedAt());
        row.setCreatedBy(override.getCreatedBy());
        row.setUpdatedAt(override.getUpdatedAt());
        row.setUpdatedBy(override.getUpdatedBy());
        return row;
    }

    private UnitScope scopeOf(RolePermissionUnitOverride override) {
        return switch (override.getScopeLevel()) {
            case DIOCESE -> unitScopeResolver.load(override.getDioceseId(), null, null);
            case DEANERY -> unitScopeResolver.load(null, override.getDeaneryId(), null);
            case PARISH -> unitScopeResolver.load(null, null, override.getParishId());
        };
    }

    private Map<java.util.UUID, String> namesOfScope(UnitScope scope) {
        Map<java.util.UUID, String> names = new LinkedHashMap<>();
        if (scope.dioceseId() != null) {
            names.put(scope.dioceseId(), scope.dioceseName());
        }
        if (scope.deaneryId() != null) {
            names.put(scope.deaneryId(), scope.deaneryName());
        }
        if (scope.parishId() != null) {
            names.put(scope.parishId(), scope.parishName());
        }
        return names;
    }

    /** Id đơn lẻ (cách gửi cũ) và danh sách id gộp thành một danh sách. */
    private static List<java.util.UUID> ids(java.util.UUID single, List<java.util.UUID> many) {
        List<java.util.UUID> all = new ArrayList<>();
        if (single != null) {
            all.add(single);
        }
        if (many != null) {
            all.addAll(many);
        }
        return all;
    }

    /**
     * Gán quyền cho vai trò tại một hoặc nhiều đơn vị (ngoại lệ theo đơn vị); trả về các dòng ngoại lệ vừa thay đổi.
     * Chọn nhiều nơi thì nơi đã có sẵn quyền được bỏ qua; chỉ báo lỗi khi không nơi nào thay đổi. Toàn bộ trong một giao dịch.
     */
    private List<RolePermissionResponse> assignForUnits(Role role, List<UnitScope> units, List<String> permissionCodes, String conditions,
                                                        OffsetDateTime assignedAt, OffsetDateTime expiresAt,
                                                        Authentication authentication, String reason) {
        String actorName = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        Actor actor = unitScopeResolver.actor(authentication, false);
        Actor revoker = unitScopeResolver.actor(authentication, true);
        List<Permission> permissions = new ArrayList<>();
        for (String permissionCode : permissionCodes) {
            Permission permission = systemPermissionRepository.findById(permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
            if (!Boolean.TRUE.equals(permission.getStatus())) {
                throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động: " + permissionCode);
            }
            unitScopeResolver.requirePermissionAllowed(actor, permission);
            permissions.add(permission);
        }
        List<RolePermissionResponse> responses = new ArrayList<>();
        phucuong.ht.common.exception.ApiException firstSkipped = null;
        for (UnitScope unit : units) {
            Map<java.util.UUID, String> names = namesOfScope(unit);
            List<Permission> granted = new ArrayList<>();
            for (Permission permission : permissions) {
                try {
                    RolePermissionUnitOverrideService.Change change = unitOverrideService.assign(role, permission, unit, conditions, assignedAt, expiresAt, actorName);
                    responses.add(toResponse(toListingRow(change.entity(), unit), authentication, revoker, names));
                    granted.add(permission);
                } catch (phucuong.ht.common.exception.ApiException ex) {
                    if (units.size() > 1 && ex.getStatus() == org.springframework.http.HttpStatus.CONFLICT) {
                        if (firstSkipped == null) {
                            firstSkipped = ex;
                        }
                        continue;
                    }
                    throw ex;
                }
            }
            if (!granted.isEmpty()) {
                notifyUnitChanged(role, unit, reason);
                if (permissionGrantNotifier != null) {
                    permissionGrantNotifier.notifyRoleGranted(role, granted, actorName, unit.unitId(), unit.label());
                }
            }
        }
        if (responses.isEmpty() && firstSkipped != null) {
            throw firstSkipped;
        }
        return responses;
    }

    /**
     * Thu hồi quyền của vai trò tại một hoặc nhiều đơn vị; trả về số lần thu hồi đã thực hiện (quyền x đơn vị).
     * Chọn nhiều nơi thì nơi chưa có quyền được bỏ qua; chỉ báo lỗi khi không nơi nào thay đổi.
     */
    private int revokeForUnits(Role role, List<UnitScope> units, List<String> permissionCodes, Authentication authentication) {
        String actorName = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi phân quyền của vai trò");
        Actor actor = unitScopeResolver.actor(authentication, true);
        authorizationHelper.requireNotOwnRole(authentication, role);
        List<Permission> permissions = new ArrayList<>();
        for (String permissionCode : permissionCodes) {
            Permission permission = systemPermissionRepository.findById(permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
            unitScopeResolver.requirePermissionAllowed(actor, permission);
            permissions.add(permission);
        }
        int total = 0;
        phucuong.ht.common.exception.ApiException firstSkipped = null;
        for (UnitScope unit : units) {
            List<Permission> revoked = new ArrayList<>();
            for (Permission permission : permissions) {
                try {
                    unitOverrideService.revoke(role, permission, unit, actorName);
                    revoked.add(permission);
                } catch (phucuong.ht.common.exception.ApiException ex) {
                    if (units.size() > 1 && ex.getStatus() == org.springframework.http.HttpStatus.NOT_FOUND) {
                        if (firstSkipped == null) {
                            firstSkipped = ex;
                        }
                        continue;
                    }
                    throw ex;
                }
            }
            if (!revoked.isEmpty()) {
                total += revoked.size();
                notifyUnitChanged(role, unit, "ROLE_PERMISSION_BULK_REVOKED");
                if (permissionGrantNotifier != null) {
                    permissionGrantNotifier.notifyRoleRevoked(role, revoked, actorName, unit.unitId(), unit.label());
                }
            }
        }
        if (total == 0 && firstSkipped != null) {
            throw firstSkipped;
        }
        return total;
    }

    private void notifyUnitChanged(Role role, UnitScope unit, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyRoleChanged(role.getRoleCode(), unit.unitId(), reason);
        }
    }

    /** Cập nhật hạn / ghi chú của quyền cấp riêng theo đơn vị; dòng thu hồi riêng không sửa được (muốn đổi thì cấp lại quyền tại đơn vị). */
    private RolePermissionResponse updateOverride(String id, RolePermissionUpdateRequest request, Authentication authentication) {
        RolePermissionUnitOverride override = unitOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        if (override.getOverrideType() != RolePermissionOverrideType.GRANT) {
            throw commonValidationHelper.conflict("Quyền đã thu hồi riêng cho đơn vị, không thể chỉnh sửa; hãy cấp lại quyền tại đơn vị");
        }
        UnitScope scope = scopeOf(override);
        Actor actor = unitScopeResolver.actor(authentication, false);
        if (!unitScopeResolver.canManageRow(actor, override.getRole(), override.getPermission(),
                override.getScopeLevel(), scope.dioceseId(), scope.deaneryId())) {
            throw commonValidationHelper.forbidden("Không có quyền cập nhật phân quyền của đơn vị này");
        }
        if (PermissionValiditySupport.isPermanentlyRevoked(override.getRevokedAt(), override.getExpiresAt(), OffsetDateTime.now())) {
            throw commonValidationHelper.conflict("Phân quyền đã bị thu hồi hoặc hết hạn, không thể thay đổi; hãy tạo phân quyền mới");
        }
        if (request.getConditions() != null) {
            override.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
        }
        if (request.getExpiresAt() != null) {
            override.setExpiresAt(request.getExpiresAt());
        }
        override.setUpdatedAt(OffsetDateTime.now());
        override.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật phân quyền cho vai trò"));
        notifyUnitChanged(override.getRole(), scope, "ROLE_PERMISSION_UPDATED");
        return toResponse(toListingRow(override, scope), authentication, unitScopeResolver.actor(authentication, true), namesOfScope(scope));
    }

    private RolePermissionResponse toResponse(RolePermission rolePermission) {
        return responseBuilder(rolePermission).build();
    }

    private RolePermissionResponse toResponse(RolePermission rolePermission, Authentication authentication) {
        boolean revocable = !PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), OffsetDateTime.now())
                && authorizationHelper.canRevokeRolePermission(authentication, rolePermission.getRole(), rolePermission.getPermission(),
                rolePermission.getAssignedBy(), rolePermission.getCreatedBy());
        return responseBuilder(rolePermission).revocable(revocable).build();
    }

    private RolePermissionResponse.RolePermissionResponseBuilder responseBuilder(RolePermission rolePermission) {
        return RolePermissionResponse.builder()
                .id(rolePermission.getId())
                .entryType(RolePermissionEntryType.DEFAULT)
                .scopeName("Tất cả giáo phận")
                .roleCode(rolePermission.getRole() == null ? null : rolePermission.getRole().getRoleCode())
                .roleName(rolePermission.getRole() == null ? null : rolePermission.getRole().getRoleName())
                .permissionCode(rolePermission.getPermission() == null ? null : rolePermission.getPermission().getPermissionCode())
                .permissionName(rolePermission.getPermission() == null ? null : rolePermission.getPermission().getPermissionName())
                .conditions(rolePermission.getConditions())
                .assignedBy(rolePermission.getAssignedBy())
                .assignedByName(auditActorNameResolver.resolve(rolePermission.getAssignedBy()))
                .assignedAt(rolePermission.getAssignedAt())
                .expiresAt(rolePermission.getExpiresAt())
                .status(rolePermission.getStatus())
                .validity(PermissionValiditySupport.validity(rolePermission.getRevokedAt(), rolePermission.getRevocationReason(),
                        rolePermission.getExpiresAt(), rolePermission.getPermission(), OffsetDateTime.now()))
                .revocationReason(rolePermission.getRevocationReason())
                .revokedAt(rolePermission.getRevokedAt())
                .revokedBy(rolePermission.getRevokedBy())
                .revokedByName(auditActorNameResolver.resolve(rolePermission.getRevokedBy()))
                .createdAt(rolePermission.getCreatedAt())
                .createdBy(rolePermission.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(rolePermission.getCreatedBy()))
                .updatedAt(rolePermission.getUpdatedAt())
                .updatedBy(rolePermission.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(rolePermission.getUpdatedBy()));
    }

    private boolean canAssignPermission(Authentication authentication, Role role, Permission permission) {
        try {
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .permissionCode(permission.getPermissionCode())
                .permissionName(permission.getPermissionName())
                .moduleCode(permission.getModule() == null ? null : permission.getModule().getModuleCode())
                .resourceCode(permission.getResource() == null ? null : permission.getResource().getResourceCode())
                .actionCode(permission.getAction() == null ? null : permission.getAction().getActionCode())
                .scopeCode(permission.getScope() == null ? null : permission.getScope().getScopeCode())
                .description(permission.getDescription())
                .isSystem(permission.getIsSystem())
                .displayOrder(permission.getDisplayOrder())
                .status(permission.getStatus())
                .createdAt(permission.getCreatedAt())
                .createdBy(permission.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(permission.getCreatedBy()))
                .updatedAt(permission.getUpdatedAt())
                .updatedBy(permission.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(permission.getUpdatedBy()))
                .build();
    }

    private RolePermission createRolePermission(Role role, Permission permission, String actor, OffsetDateTime assignedAt) {
        RolePermissionCreateRequest createRequest = new RolePermissionCreateRequest();
        createRequest.setRoleCode(role.getRoleCode());
        createRequest.setPermissionCode(permission.getPermissionCode());
        createRequest.setAssignedAt(assignedAt);
        RolePermission rolePermission = rolePermissionRepository.create(createRequest, actor);
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(assignedAt);
        rolePermission.setStatus(true);
        rolePermission.setUpdatedAt(OffsetDateTime.now());
        rolePermission.setUpdatedBy(actor);
        return rolePermission;
    }
}
