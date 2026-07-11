package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountPermissionOverride;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideCreateRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountPermissionOverrideResponse;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountPermissionOverrideManagementService {

    private final AccountPermissionOverrideRepository accountPermissionOverrideRepository;
    private final AccountRepository accountRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;

    public AccountPermissionOverrideResponse create(AccountPermissionOverrideCreateRequest request, Authentication authentication) {
        Account account = accountRepository.findActiveByUsername(request.getUsername())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + request.getUsername()));
        Permission permission = systemPermissionRepository.findById(request.getPermissionCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + request.getPermissionCode()));
        if (!Boolean.TRUE.equals(permission.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động");
        }

        authorizationHelper.requireAccountPermissionWriteAccess(authentication, request.getUsername(), permission);

        if (accountPermissionOverrideRepository.findByUsernameAndPermissionCode(request.getUsername(), request.getPermissionCode()).isPresent()) {
            throw commonValidationHelper.conflict("Phân quyền ghi đè cho tài khoản đã tồn tại");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho tài khoản");
        AccountPermissionOverride override = accountPermissionOverrideRepository.create(request, actor);
        return toResponse(override);
    }

    public Page<AccountPermissionOverrideResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        Page<AccountPermissionOverride> page = accountPermissionOverrideRepository.findAll(copyRequest(request));
        var content = page.getContent().stream()
                .filter(override -> authorizationHelper.canManageAccountPermission(authentication, override.getAccount().getUsername(), override.getPermission()))
                .map(this::toResponse).toList();
        return new PageImpl<>(content, page.getPageable(), content.size());
    }

    public AccountPermissionOverrideResponse findById(String id) {
        return toResponse(accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền ghi đè của tài khoản: " + id)));
    }

    public AccountPermissionOverrideResponse update(String id, AccountPermissionOverrideUpdateRequest request, Authentication authentication) {
        AccountPermissionOverride override = accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền ghi đè của tài khoản: " + id));
        authorizationHelper.requireAccountPermissionWriteAccess(authentication, override.getAccount().getUsername(), override.getPermission());

        if (request.getEffect() != null) {
            override.setEffect(request.getEffect());
        }
        if (request.getReason() != null) {
            override.setReason(commonValidationHelper.normalizeOptionalText(request.getReason()));
        }
        if (request.getExpiresAt() != null) {
            override.setExpiresAt(request.getExpiresAt());
        }
        override.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật phân quyền cho tài khoản"));
        return toResponse(override);
    }

    public void delete(String id, Authentication authentication) {
        AccountPermissionOverride override = accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền ghi đè của tài khoản: " + id));
        authorizationHelper.requireAccountPermissionWriteAccess(authentication, override.getAccount().getUsername(), override.getPermission());
        accountPermissionOverrideRepository.delete(id);
    }

    public AccountPermissionOverrideResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        AccountPermissionOverride override = accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền ghi đè của tài khoản: " + id));
        authorizationHelper.requireAccountPermissionWriteAccess(authentication, override.getAccount().getUsername(), override.getPermission());
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái phân quyền cho tài khoản");
        accountPermissionOverrideRepository.toggleStatus(id, actor);
        return findById(id);
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private AccountPermissionOverrideResponse toResponse(AccountPermissionOverride override) {
        return AccountPermissionOverrideResponse.builder()
                .id(override.getId())
                .username(override.getAccount() == null ? null : override.getAccount().getUsername())
                .permissionCode(override.getPermission() == null ? null : override.getPermission().getPermissionCode())
                .effect(override.getEffect())
                .reason(override.getReason())
                .expiresAt(override.getExpiresAt())
                .status(override.getStatus())
                .build();
    }
}
