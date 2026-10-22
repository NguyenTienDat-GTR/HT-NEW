package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionManagementServiceTest {

    @Mock
    private SystemPermissionRepository systemPermissionRepository;

    @Mock
    private SystemAdminAuthorizationHelper authorizationHelper;

    @Test
    void updateShouldOnlyChangeMetadata() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        PermissionManagementService service = new PermissionManagementService(systemPermissionRepository, helper, authorizationHelper);

        Permission permission = new Permission();
        permission.setPermissionCode("system.permission.read.all");
        permission.setPermissionName("Old name");
        permission.setDescription("Old description");
        permission.setDisplayOrder(10);
        when(systemPermissionRepository.findById("system.permission.read.all")).thenReturn(Optional.of(permission));

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setPermissionName("  New name  ");
        request.setDescription("  New description  ");
        request.setDisplayOrder(20);
        when(systemPermissionRepository.save(permission)).thenReturn(permission);

        service.update("system.permission.read.all", request, authentication(List.of("SUPER_ADMIN")));

        assertEquals("New name", permission.getPermissionName());
        assertEquals("New description", permission.getDescription());
        assertEquals(20, permission.getDisplayOrder());
        verify(systemPermissionRepository).save(permission);
    }

    private UsernamePasswordAuthenticationToken authentication(List<String> roles) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("user", null, roles, List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Permission permission(String permissionCode) {
        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(permissionCode);
        return permission;
    }
}
