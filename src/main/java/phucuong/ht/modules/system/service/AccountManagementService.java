package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.modules.system.model.request.AccountUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountManagementService {
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final LeaderRepository leaderRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommonValidationHelper validation;
    private final AccountManagementAuthorizationHelper authorizationHelper;

    @Transactional
    public AccountResponse create(AccountCreateRequest request, Authentication authentication) {
        String username = request.getUsername().trim();
        if (accountRepository.existsByUsername(username)) throw validation.conflict("Tên đăng nhập đã tồn tại");
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        apply(account, request.getLeaderId(), request.getDioceseId(), request.getDeaneryId(), request.getParishId(), request.getAuthProvider(), request.getProviderId());
        Role role = roleRepository.findByRoleCodeAndStatusTrue(request.getRoleCode())
                .orElseThrow(() -> validation.notFound("Không tìm thấy role " + request.getRoleCode()));
        authorizationHelper.requireCanCreate(authentication, account, role);
        String actor = validation.requireAuditActor(authentication, "Không xác định được người tạo tài khoản");
        account.setStatus(true);
        account.setCreatedBy(actor);
        account.setUpdatedBy(actor);
        Account saved = accountRepository.save(account);
        assignInitialRole(saved, role, actor);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> findAll(Pageable pageable, Authentication authentication) {
        var manageable = accountRepository.findAll().stream()
                .filter(account -> authorizationHelper.canRead(authentication, account))
                .toList();
        int start = Math.min((int) pageable.getOffset(), manageable.size());
        int end = Math.min(start + pageable.getPageSize(), manageable.size());
        return new PageImpl<>(manageable.subList(start, end).stream().map(this::response).toList(), pageable, manageable.size());
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(String username, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanManage(authentication, account);
        return response(account);
    }

    @Transactional
    public AccountResponse update(String username, AccountUpdateRequest request, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanManage(authentication, account);
        if (request.getPassword() != null && !request.getPassword().isBlank()) account.setPassword(passwordEncoder.encode(request.getPassword()));
        if (Boolean.TRUE.equals(request.getClearDirectScope()) || request.getDioceseId() != null || request.getDeaneryId() != null || request.getParishId() != null) {
            account.setDiocese(null);
            account.setDeanery(null);
            account.setParish(null);
        }
        apply(account, request.getLeaderId(), request.getDioceseId(), request.getDeaneryId(), request.getParishId(), request.getAuthProvider(), request.getProviderId());
        authorizationHelper.requireCanManage(authentication, account);
        account.setUpdatedBy(validation.requireAuditActor(authentication, "Không xác định được người cập nhật tài khoản"));
        return response(account);
    }

    @Transactional
    public void delete(String username, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanManage(authentication, account);
        accountRepository.delete(account);
    }

    @Transactional
    public AccountResponse toggleStatus(String username, StatusToggleRequest request, Authentication authentication) {
        validation.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        Account account = require(username);
        authorizationHelper.requireCanManage(authentication, account);
        account.setStatus(!Boolean.TRUE.equals(account.getStatus()));
        account.setUpdatedBy(validation.requireAuditActor(authentication, "Không xác định được người đổi trạng thái tài khoản"));
        return response(account);
    }

    private Account require(String username) {
        return accountRepository.findById(username).orElseThrow(() -> validation.notFound("Không tìm thấy tài khoản: " + username));
    }

    private void assignInitialRole(Account account, Role role, String actor) {
        OffsetDateTime now = OffsetDateTime.now();
        AccountRole accountRole = new AccountRole();
        accountRole.setAccount(account);
        accountRole.setRole(role);
        accountRole.setIsPrimary(true);
        accountRole.setAssignedBy(actor);
        accountRole.setAssignedAt(now);
        accountRole.setStatus(true);
        accountRole.setCreatedAt(now);
        accountRole.setUpdatedAt(now);
        accountRole.setCreatedBy(actor);
        accountRole.setUpdatedBy(actor);
        accountRoleRepository.save(accountRole);
    }

    private void apply(Account a, UUID leaderId, UUID dioceseId, UUID deaneryId, UUID parishId, String provider, String providerId) {
        int directScopeCount = (dioceseId == null ? 0 : 1) + (deaneryId == null ? 0 : 1) + (parishId == null ? 0 : 1);
        if (directScopeCount > 1) throw validation.validationError("Tài khoản chỉ được có một direct scope: dioceseId, deaneryId hoặc parishId");
        if (leaderId != null) a.setLeader(leaderRepository.findById(leaderId).orElseThrow(() -> validation.notFound("Không tìm thấy leader")));
        if (dioceseId != null) a.setDiocese(dioceseRepository.findById(dioceseId).orElseThrow(() -> validation.notFound("Không tìm thấy giáo phận")));
        if (deaneryId != null) a.setDeanery(deaneryRepository.findById(deaneryId).orElseThrow(() -> validation.notFound("Không tìm thấy giáo hạt")));
        if (parishId != null) a.setParish(parishRepository.findById(parishId).orElseThrow(() -> validation.notFound("Không tìm thấy giáo xứ")));
        if (provider != null) a.setAuthProvider(provider);
        if (providerId != null) a.setProviderId(providerId);
    }

    private AccountResponse response(Account a) {
        return AccountResponse.builder().username(a.getUsername()).leaderId(a.getLeader() == null ? null : a.getLeader().getId())
                .dioceseId(a.getDiocese() == null ? null : a.getDiocese().getId()).deaneryId(a.getDeanery() == null ? null : a.getDeanery().getId())
                .parishId(a.getParish() == null ? null : a.getParish().getId()).authProvider(a.getAuthProvider()).providerId(a.getProviderId())
                .status(a.getStatus()).build();
    }
}
