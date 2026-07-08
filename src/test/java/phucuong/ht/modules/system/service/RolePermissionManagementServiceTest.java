package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.PermissionScope;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.enums.PermissionEffect;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionManagementServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SystemPermissionRepository systemPermissionRepository;

    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void createShouldRejectDioceseAdminAssigningAllScopePermission() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                null,
                roleRepository,
                helper
        );
        RolePermissionManagementService service = new RolePermissionManagementService(
                rolePermissionRepository,
                roleRepository,
                systemPermissionRepository,
                helper,
                authorizationHelper
        );

        Role targetRole = new Role();
        targetRole.setRoleCode("ADMIN_PARISH");
        targetRole.setDisplayOrder(300);

        Role adminDiocese = new Role();
        adminDiocese.setRoleCode("ADMIN_DIOCESE");
        adminDiocese.setDisplayOrder(100);
        adminDiocese.setStatus(true);

        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("all");
        Permission permission = new Permission();
        permission.setPermissionCode("system.role.read.all");
        permission.setScope(scope);

        RolePermissionCreateRequest request = new RolePermissionCreateRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCode("system.role.read.all");
        request.setEffect(PermissionEffect.ALLOW);

        when(roleRepository.findById("ADMIN_PARISH")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(adminDiocese));
        when(systemPermissionRepository.findById("system.role.read.all")).thenReturn(Optional.of(permission));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of("system.role_permission.assign.diocese")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertThrows(RuntimeException.class, () -> service.create(request, authentication));
    }

    @Test
    void createShouldRejectDioceseAdminAssigningHigherRole() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                null,
                roleRepository,
                helper
        );
        RolePermissionManagementService service = new RolePermissionManagementService(
                rolePermissionRepository,
                roleRepository,
                systemPermissionRepository,
                helper,
                authorizationHelper
        );

        Role targetRole = new Role();
        targetRole.setRoleCode("CUSTOM_HIGH");
        targetRole.setDisplayOrder(50);

        Role adminDiocese = new Role();
        adminDiocese.setRoleCode("ADMIN_DIOCESE");
        adminDiocese.setDisplayOrder(100);
        adminDiocese.setStatus(true);

        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("diocese");
        Permission permission = new Permission();
        permission.setPermissionCode("organization.position.read.diocese");
        permission.setScope(scope);

        RolePermissionCreateRequest request = new RolePermissionCreateRequest();
        request.setRoleCode("CUSTOM_HIGH");
        request.setPermissionCode("organization.position.read.diocese");
        request.setEffect(PermissionEffect.ALLOW);

        when(roleRepository.findById("CUSTOM_HIGH")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(adminDiocese));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of("system.role_permission.assign.diocese")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertThrows(RuntimeException.class, () -> service.create(request, authentication));
    }
}
