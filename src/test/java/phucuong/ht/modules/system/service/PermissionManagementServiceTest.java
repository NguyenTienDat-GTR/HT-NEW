package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.request.PermissionCreateRequest;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionManagementServiceTest {

    @Mock
    private SystemPermissionRepository systemPermissionRepository;

    @Test
    void createShouldGeneratePermissionCodeAndForceNonSystem() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        PermissionManagementService service = new PermissionManagementService(systemPermissionRepository, helper);

        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setPermissionName("  Quan ly quyen  ");
        request.setModuleCode("System");
        request.setResourceCode("Permission");
        request.setActionCode("Read");
        request.setScopeCode("Diocese");
        request.setDescription("  Mo ta  ");
        request.setDisplayOrder(15);

        when(systemPermissionRepository.existsModuleCode("system")).thenReturn(true);
        when(systemPermissionRepository.existsResourceCodeInModule("permission", "system")).thenReturn(true);
        when(systemPermissionRepository.existsActionCode("read")).thenReturn(true);
        when(systemPermissionRepository.existsScopeCode("diocese")).thenReturn(true);
        when(systemPermissionRepository.findById("system.permission.read.diocese")).thenReturn(Optional.empty());

        Permission saved = new Permission();
        saved.setPermissionCode("system.permission.read.diocese");
        saved.setPermissionName("Quan ly quyen");
        saved.setIsSystem(false);
        saved.setStatus(true);
        when(systemPermissionRepository.create(any(), eq("super-admin"))).thenReturn(saved);

        service.create(request, authentication(List.of("SUPER_ADMIN")));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(systemPermissionRepository).create(payloadCaptor.capture(), eq("super-admin"));
        Object payload = payloadCaptor.getValue();
        assertEquals("system.permission.read.diocese", readProperty(payload, "getPermissionCode"));
        assertEquals("Quan ly quyen", readProperty(payload, "getPermissionName"));
        assertEquals("system", readProperty(payload, "getModuleCode"));
        assertEquals("permission", readProperty(payload, "getResourceCode"));
        assertEquals("read", readProperty(payload, "getActionCode"));
        assertEquals("diocese", readProperty(payload, "getScopeCode"));
        assertFalse((Boolean) readProperty(payload, "getIsSystem"));
    }

    @Test
    void createShouldRejectDuplicateCombination() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        PermissionManagementService service = new PermissionManagementService(systemPermissionRepository, helper);

        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setPermissionName("Custom permission");
        request.setModuleCode("system");
        request.setResourceCode("permission");
        request.setActionCode("read");
        request.setScopeCode("all");

        when(systemPermissionRepository.existsModuleCode("system")).thenReturn(true);
        when(systemPermissionRepository.existsResourceCodeInModule("permission", "system")).thenReturn(true);
        when(systemPermissionRepository.existsActionCode("read")).thenReturn(true);
        when(systemPermissionRepository.existsScopeCode("all")).thenReturn(true);
        when(systemPermissionRepository.findById("system.permission.read.all")).thenReturn(Optional.of(new Permission()));

        assertThrows(ApiException.class, () -> service.create(request, authentication(List.of("SUPER_ADMIN"))));
    }

    @Test
    void deleteShouldRejectSystemPermission() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        PermissionManagementService service = new PermissionManagementService(systemPermissionRepository, helper);

        Permission permission = new Permission();
        permission.setPermissionCode("system.permission.read.all");
        permission.setIsSystem(true);
        when(systemPermissionRepository.findById("system.permission.read.all")).thenReturn(Optional.of(permission));

        assertThrows(ApiException.class, () -> service.delete("system.permission.read.all"));
    }

    @Test
    void updateShouldOnlyChangeMetadata() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        PermissionManagementService service = new PermissionManagementService(systemPermissionRepository, helper);

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

        service.update("system.permission.read.all", request, authentication(List.of("SUPER_ADMIN")));

        assertEquals("New name", permission.getPermissionName());
        assertEquals("New description", permission.getDescription());
        assertEquals(20, permission.getDisplayOrder());
    }

    private UsernamePasswordAuthenticationToken authentication(List<String> roles) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("user", null, roles, List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Object readProperty(Object target, String getterName) {
        try {
            return target.getClass().getMethod(getterName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
