package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRoleService {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    private static final String ROLE_ADMIN_DEANERY = "ADMIN_DEANERY";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper commonValidationHelper;

    @Transactional
    public AccountRoleResponse assignRole(AccountRoleCreateRequest request, Authentication authentication) {
        Account account = accountRepository.findById(request.getUsername())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy account " + request.getUsername()));
        Role targetRole = roleRepository.findByRoleCodeAndStatusTrue(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy role " + request.getRoleCode()));

        enforceAssignmentScope(authentication, account, targetRole);

        OffsetDateTime now = OffsetDateTime.now();
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gán role");

        AccountRole accountRole = new AccountRole();
        accountRole.setAccount(account);
        accountRole.setRole(targetRole);
        accountRole.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        accountRole.setAssignedBy(actor);
        accountRole.setAssignedAt(request.getAssignedAt() == null ? now : request.getAssignedAt());
        accountRole.setExpiresAt(request.getExpiresAt());
        accountRole.setStatus(Boolean.TRUE);
        accountRole.setCreatedAt(now);
        accountRole.setUpdatedAt(now);
        accountRole.setCreatedBy(actor);
        accountRole.setUpdatedBy(actor);

        AccountRole saved = accountRoleRepository.save(accountRole);
        return toResponse(saved);
    }

    private void enforceAssignmentScope(Authentication authentication, Account targetAccount, Role targetRole) {
        if (hasRole(authentication, ROLE_SUPER_ADMIN)) {
            return;
        }

        if (ROLE_ADMIN_DIOCESE.equals(targetRole.getRoleCode())) {
            throw commonValidationHelper.forbidden("Chỉ SUPER_ADMIN được gán role ADMIN_DIOCESE");
        }

        if (hasAuthority(authentication, "system.account_role.assign.diocese")) {
            UUID actorDioceseId = accountRepository.findActiveDioceseIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có scope giáo phận hợp lệ"));
            UUID targetDioceseId = accountRepository.findActiveDioceseIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Account đích chưa có scope giáo phận hợp lệ để gán role"));
            if (!actorDioceseId.equals(targetDioceseId)) {
                throw commonValidationHelper.forbidden("Không thể gán role cho account ngoài giáo phận hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_DIOCESE);
            return;
        }

        if (hasAuthority(authentication, "system.account_role.assign.deanery")) {
            UUID actorDeaneryId = accountRepository.findActiveDeaneryIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có scope giáo hạt hợp lệ"));
            UUID targetDeaneryId = accountRepository.findActiveDeaneryIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Account đích chưa có scope giáo hạt hợp lệ để gán role"));
            if (!actorDeaneryId.equals(targetDeaneryId)) {
                throw commonValidationHelper.forbidden("Không thể gán role cho account ngoài giáo hạt hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_DEANERY);
            return;
        }

        throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gán role");
    }

    private void enforceHierarchy(Authentication authentication, Role targetRole, String actorRoleCode) {
        Role actorRole = roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy role " + actorRoleCode));

        Integer actorOrder = actorRole.getDisplayOrder();
        Integer targetOrder = targetRole.getDisplayOrder();
        if (actorOrder == null || targetOrder == null || targetOrder < actorOrder) {
            throw commonValidationHelper.forbidden("Không thể gán role cao hơn phạm vi của tài khoản hiện tại");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private boolean hasRole(Authentication authentication, String roleCode) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            return false;
        }
        return principal.getRoles().stream().anyMatch(roleCode::equals);
    }

    private String currentUsername(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        return principal.getUsername();
    }

    private AccountRoleResponse toResponse(AccountRole accountRole) {
        return AccountRoleResponse.builder()
                .id(accountRole.getId())
                .username(accountRole.getAccount().getUsername())
                .roleCode(accountRole.getRole().getRoleCode())
                .isPrimary(accountRole.getIsPrimary())
                .assignedBy(accountRole.getAssignedBy())
                .assignedAt(accountRole.getAssignedAt())
                .expiresAt(accountRole.getExpiresAt())
                .status(accountRole.getStatus())
                .build();
    }
}
