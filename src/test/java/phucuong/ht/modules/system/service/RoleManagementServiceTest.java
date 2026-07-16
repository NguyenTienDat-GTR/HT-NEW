package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.RoleCreateRequest;
import phucuong.ht.modules.system.model.request.RoleUpdateRequest;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemRoleRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock
    private SystemRoleRepository roleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private CommonValidationHelper validationHelper;

    @Mock
    private SystemAdminAuthorizationHelper authorizationHelper;

    @Mock
    private Authentication authentication;

    @Test
    void createShouldGenerateRoleCodeFromRoleNameWithNumericSuffix() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleName("Lãnh đạo");
        request.setDescription("  Quản lý đơn vị  ");

        when(validationHelper.requireAuditActor(authentication, "Không xác định được người tạo vai trò")).thenReturn("super-admin");
        when(validationHelper.normalizeRequiredText("Lãnh đạo")).thenReturn("Lãnh đạo");
        when(validationHelper.normalizeOptionalText("  Quản lý đơn vị  ")).thenReturn("Quản lý đơn vị");
        when(roleRepository.existsByRoleCode("LANH_DAO")).thenReturn(true);
        when(roleRepository.existsByRoleCode("LANH_DAO2")).thenReturn(false);
        when(roleRepository.createRole(any(RoleCreateRequest.class), eq("super-admin"))).thenAnswer(invocation -> {
            RoleCreateRequest captured = invocation.getArgument(0);
            Role role = new Role();
            role.setRoleCode(captured.getRoleCode());
            role.setRoleName(captured.getRoleName());
            role.setDescription(captured.getDescription());
            role.setIsSystem(false);
            role.setStatus(true);
            return role;
        });

        RoleManagementService service = service();
        var response = service.create(request, authentication);

        assertEquals("LANH_DAO2", response.getRoleCode());
        assertEquals("Lãnh đạo", response.getRoleName());
        assertEquals("Quản lý đơn vị", response.getDescription());
    }

    @Test
    void toggleStatusShouldCascadeRolePermissionStatus() {
        Role before = role("CUSTOM_ROLE", true);
        Role after = role("CUSTOM_ROLE", false);
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");

        when(roleRepository.findById("CUSTOM_ROLE")).thenReturn(Optional.of(before));
        when(validationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái vai trò")).thenReturn("super-admin");
        when(roleRepository.toggleStatus("CUSTOM_ROLE", "super-admin")).thenReturn(after);

        RoleManagementService service = service();
        var response = service.toggleStatus("CUSTOM_ROLE", request, authentication);

        assertEquals(false, response.getStatus());
        ArgumentCaptor<OffsetDateTime> updatedAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(validationHelper).requireStatusToggleConfirmation("Xác nhận");
        verify(rolePermissionRepository).updateStatusByRoleCode(eq("CUSTOM_ROLE"), eq(false), updatedAt.capture(), eq("super-admin"));
    }

    @Test
    void updateShouldSaveRoleAfterApplyingChanges() {
        Role role = role("CUSTOM_ROLE", true);
        role.setRoleName("Tên cũ");
        role.setDescription("Mô tả cũ");
        role.setDisplayOrder(1);
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleName("Tên mới");
        request.setDescription("Mô tả mới");
        request.setDisplayOrder(9);

        when(roleRepository.findById("CUSTOM_ROLE")).thenReturn(Optional.of(role));
        when(validationHelper.normalizeRequiredText("Tên mới")).thenReturn("Tên mới");
        when(validationHelper.normalizeOptionalText("Mô tả mới")).thenReturn("Mô tả mới");
        when(validationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật vai trò")).thenReturn("super-admin");
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().update("CUSTOM_ROLE", request, authentication);

        assertEquals("Tên mới", response.getRoleName());
        assertEquals("Mô tả mới", response.getDescription());
        assertEquals(9, response.getDisplayOrder());
        verify(roleRepository).save(role);
    }

    private RoleManagementService service() {
        return new RoleManagementService(roleRepository, rolePermissionRepository, validationHelper, authorizationHelper);
    }

    private Role role(String roleCode, boolean status) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setIsSystem(false);
        role.setStatus(status);
        return role;
    }
}
