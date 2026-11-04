package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import phucuong.ht.common.repository.InMemoryPager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.AuthProvider;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.auth.service.AccountEmailResolver;
import phucuong.ht.modules.auth.service.EmailMasker;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.modules.system.model.response.AccountPasswordResetResponse;
import phucuong.ht.modules.system.model.response.AccountResponse;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private static final String ADMIN_DIOCESE = "ADMIN_DIOCESE";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final List<String> SUPER_ADMIN_ACCOUNT_LIST_ROLES = List.of(SUPER_ADMIN, ADMIN_DIOCESE);
    private static final List<String> ACCOUNT_PERMISSION_ADMIN_ROLES = List.of(ADMIN_DIOCESE, "ADMIN_DEANERY", "ADMIN_PARISH");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final java.time.Duration TEMPORARY_PASSWORD_TTL = java.time.Duration.ofMinutes(10);

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final LeaderRepository leaderRepository;
    private final DioceseRepository dioceseRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommonValidationHelper validation;
    private final AccountManagementAuthorizationHelper authorizationHelper;
    private final AccountCredentialMailService mailService;
    private final RedisTokenSessionService redisTokenSessionService;
    private final AccountEmailResolver emailResolver;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;

    /**
     * Tạo tài khoản và gửi email mật khẩu tạm trong cùng một transaction.
     * Ghi DB và flush trước, gửi email là bước cuối: lỗi ở bất kỳ bước nào (kể cả gửi email) đều rollback toàn bộ.
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountResponse create(AccountCreateRequest request, Authentication authentication) {
        List<String> roleCodes = normalizeCreateRoleCodes(request);
        if (roleCodes.isEmpty()) {
            throw validation.validationError("Vui lòng chọn ít nhất một vai trò");
        }
        AccountRoleCombinationPolicy.require(roleCodes, validation);
        String primaryRoleCode = resolveCreatePrimaryRole(request, roleCodes);
        List<Role> roles = roleCodes.stream()
                .map(roleCode -> roleRepository.findByRoleCodeAndStatusTrue(roleCode)
                        .orElseThrow(() -> validation.notFound("Không tìm thấy vai trò " + roleCode)))
                .toList();
        Role primaryRole = roles.stream()
                .filter(role -> primaryRoleCode.equals(role.getRoleCode()))
                .findFirst()
                .orElseThrow(() -> validation.validationError("Vai trò chính phải nằm trong danh sách vai trò đã chọn"));
        boolean superAdminRole = SUPER_ADMIN.equals(primaryRole.getRoleCode());
        boolean dioceseAdmin = ADMIN_DIOCESE.equals(primaryRole.getRoleCode());
        // SUPER_ADMIN tạo tài khoản cho vai trò toàn hệ thống do super-admin tạo, theo cách của tài khoản không gắn Huynh Trưởng.
        boolean systemWideRole = !superAdminRole && !dioceseAdmin && isSuperAdmin(authentication);
        if (systemWideRole) {
            requireSystemWideRoles(roles);
        }
        if ((superAdminRole || dioceseAdmin) && roles.size() != 1) {
            throw validation.validationError("Tài khoản SUPER_ADMIN hoặc ADMIN_DIOCESE chỉ được gán một vai trò");
        }
        if (!superAdminRole && !dioceseAdmin && roleCodes.stream().anyMatch(roleCode -> SUPER_ADMIN.equals(roleCode) || ADMIN_DIOCESE.equals(roleCode))) {
            throw validation.validationError("Không được chọn vai trò SUPER_ADMIN hoặc ADMIN_DIOCESE cho luồng tạo tài khoản này");
        }

        Account account = new Account();
        Leader credentialLeader = null;
        String recipientEmail;
        String recipientName;
        String usernameBase;
        if (superAdminRole) {
            if (request.getLeaderId() != null || request.getDioceseId() != null) {
                throw validation.validationError("Tài khoản SUPER_ADMIN không gắn Huynh Trưởng hoặc giáo phận");
            }
            recipientEmail = requireCredentialEmail(request.getCredentialEmail());
            recipientName = "Super admin";
            usernameBase = usernameBaseFromEmail(recipientEmail);
            account.setEmail(recipientEmail);
        } else if (dioceseAdmin) {
            Diocese diocese = requireDioceseForUpdate(request.getDioceseId());
            requireUnitOperational(UnitType.DIOCESE, diocese.getId());
            account.setDiocese(diocese);
            if (accountRepository.existsAdminDioceseByDioceseId(diocese.getId())) {
                throw validation.conflict("Mỗi giáo phận chỉ được có một tài khoản ADMIN_DIOCESE");
            }
            if (request.getLeaderId() != null) {
                credentialLeader = requireLeader(request.getLeaderId(), "Không tìm thấy Huynh Trưởng");
                requireLeaderInsideDiocese(credentialLeader, diocese.getId());
                requireUnitOperational(UnitType.PARISH, credentialLeader.getParish().getId());
                account.setLeader(credentialLeader);
                recipientEmail = requireCredentialEmail(credentialLeader.getEmail());
                recipientName = fullName(credentialLeader);
                usernameBase = generateLeaderUsernameBase(credentialLeader);
                if (request.getCredentialEmail() != null) {
                    throw validation.validationError("credentialEmail chỉ dùng khi ADMIN_DIOCESE không gắn Huynh Trưởng");
                }
            } else {
                recipientEmail = requireCredentialEmail(request.getCredentialEmail());
                recipientName = "Quản trị viên " + diocese.getName();
                usernameBase = normalize(diocese.getName()) + "admin";
                account.setEmail(recipientEmail);
            }
        } else if (systemWideRole) {
            if (request.getLeaderId() != null || request.getDioceseId() != null) {
                throw validation.validationError("Tài khoản vai trò toàn hệ thống không gắn Huynh Trưởng hoặc giáo phận");
            }
            recipientEmail = requireCredentialEmail(request.getCredentialEmail());
            recipientName = primaryRole.getRoleName() == null || primaryRole.getRoleName().isBlank()
                    ? primaryRole.getRoleCode() : primaryRole.getRoleName();
            usernameBase = usernameBaseFromEmail(recipientEmail);
            account.setEmail(recipientEmail);
        } else {
            if (request.getDioceseId() != null || request.getCredentialEmail() != null) {
                throw validation.validationError("Tài khoản không phải ADMIN_DIOCESE không dùng dioceseId hoặc credentialEmail");
            }
            credentialLeader = requireLeader(request.getLeaderId(), "leaderId là bắt buộc với tài khoản không phải ADMIN_DIOCESE");
            requireUnitOperational(UnitType.PARISH, credentialLeader.getParish().getId());
            account.setLeader(credentialLeader);
            recipientEmail = requireCredentialEmail(credentialLeader.getEmail());
            recipientName = fullName(credentialLeader);
            usernameBase = generateLeaderUsernameBase(credentialLeader);
        }

        roles.forEach(role -> authorizationHelper.requireCanCreate(authentication, account, role));
        String username = nextAvailableUsername(usernameBase);
        String rawPassword = generateTemporaryPassword();
        String actor = validation.requireAuditActor(authentication, "Không xác định được người tạo tài khoản");
        OffsetDateTime now = OffsetDateTime.now();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(rawPassword));
        account.setAuthProvider(AuthProvider.LOCAL.name());
        account.setMustChangePassword(Boolean.TRUE);
        account.setStatus(true);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(actor);
        account.setUpdatedBy(actor);
        Account saved = accountRepository.saveAndFlush(account);
        assignInitialRoles(saved, roles, primaryRoleCode, actor);
        accountRoleRepository.flush();
        // Lỗi ràng buộc DB lộ ra ở các lần flush trên, trước khi email được gửi đi.
        AccountResponse response = response(saved, authentication);
        mailService.sendInitialCredentials(recipientEmail, recipientName, username, rawPassword);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest searchRequest = request == null ? new BaseSearchRequest() : request;
        List<Account> accounts = accountRepository.findAll();
        // Vai trò của mọi tài khoản trong một câu, thay vì vài câu cho mỗi tài khoản khi lọc quyền xem và dựng dữ liệu.
        phucuong.ht.config.AccountLookupMemoConfig.preloadActiveRoles(accountRoleRepository, accounts.stream().map(Account::getUsername).toList());
        List<AccountResponse> manageable = accounts.stream()
                .filter(account -> isVisibleInAccountList(authentication, account))
                .map(account -> response(account, authentication))
                .filter(response -> matchesAccountFilters(response, searchRequest))
                .toList();
        return page(searchRequest, manageable);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAccountPermissionTargets(Authentication authentication, String search) {
        String normalizedSearch = validation.normalizeOptionalText(search);
        List<Account> accounts = accountRepository.findAll();
        phucuong.ht.config.AccountLookupMemoConfig.preloadActiveRoles(accountRoleRepository, accounts.stream().map(Account::getUsername).toList());
        return accounts.stream()
                .filter(account -> Boolean.TRUE.equals(account.getStatus()))
                .filter(account -> isSuperAdmin(authentication) ? isValidAccountPermissionTarget(account) : authorizationHelper.canManageForPermissions(authentication, account))
                .map(this::response)
                .filter(response -> matchesUsernameSearch(response, normalizedSearch))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(String username, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanRead(authentication, account);
        return response(account, authentication);
    }

    @Transactional
    public AccountResponse toggleStatus(String username, StatusToggleRequest request, Authentication authentication) {
        validation.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        Account account = require(username);
        requireAccountWritable(account.getUsername());
        authorizationHelper.requireCanManage(authentication, account);
        authorizationHelper.requireOwnedByActorScope(authentication, account);
        account.setStatus(!Boolean.TRUE.equals(account.getStatus()));
        account.setUpdatedAt(OffsetDateTime.now());
        account.setUpdatedBy(validation.requireAuditActor(authentication, "Không xác định được người đổi trạng thái tài khoản"));
        redisTokenSessionService.revokeAllSessions(account.getUsername());
        return response(account, authentication);
    }

    /**
     * Đặt lại mật khẩu: sinh mật khẩu tạm 8 chữ cái, hạn 10 phút, bắt buộc đổi khi đăng nhập, gửi qua email.
     * Ghi DB và flush trước, gửi email rồi mới thu hồi phiên: email lỗi thì rollback, mật khẩu cũ giữ nguyên và
     * các phiên đang đăng nhập không bị ảnh hưởng. Mật khẩu tạm không bao giờ trả về qua API.
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountPasswordResetResponse resetPassword(String username, Authentication authentication) {
        Account account = require(username);
        if (!Boolean.TRUE.equals(account.getStatus())) {
            throw validation.validationError("Tài khoản đang bị khóa, không thể đặt lại mật khẩu");
        }
        requireAccountWritable(account.getUsername());
        authorizationHelper.requireCanResetPassword(authentication, account);
        authorizationHelper.requireOwnedByActorScope(authentication, account);

        String email = emailResolver.resolve(account);
        if (email == null) {
            throw validation.validationError("Tài khoản chưa có email nhận mật khẩu tạm, vui lòng cập nhật email Huynh Trưởng trước");
        }
        String actor = validation.requireAuditActor(authentication, "Không xác định được người đặt lại mật khẩu");
        String actorUsername = authentication.getPrincipal() instanceof AuthUserDetails principal
                ? principal.getUsername()
                : actor;
        String rawPassword = TemporaryPasswordGenerator.generate();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plus(TEMPORARY_PASSWORD_TTL);

        account.setPassword(passwordEncoder.encode(rawPassword));
        account.setMustChangePassword(Boolean.TRUE);
        account.setTemporaryPasswordExpiresAt(expiresAt);
        account.setUpdatedAt(now);
        account.setUpdatedBy(actor);
        accountRepository.saveAndFlush(account);

        String recipientName = account.getLeader() != null ? fullName(account.getLeader()) : account.getUsername();
        mailService.sendResetPassword(email, recipientName, account.getUsername(), rawPassword, expiresAt, actorUsername);
        redisTokenSessionService.revokeAllSessions(account.getUsername());
        log.info("Tài khoản {} đã được {} đặt lại mật khẩu tạm, hết hạn lúc {}", account.getUsername(), actorUsername, expiresAt);

        return AccountPasswordResetResponse.builder()
                .username(account.getUsername())
                .maskedEmail(EmailMasker.mask(email))
                .expiresAt(expiresAt)
                .build();
    }

    private Account require(String username) {
        return accountRepository.findById(username)
                .orElseThrow(() -> validation.notFound("Không tìm thấy tài khoản: " + username));
    }

    private void requireUnitOperational(UnitType unitType, UUID unitId) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireUnitOperational(unitType, unitId);
        }
    }

    private void requireAccountWritable(String username) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireAccountWritable(username);
        }
    }

    private Leader requireLeader(UUID id, String missingMessage) {
        if (id == null) throw validation.validationError(missingMessage);
        Leader leader = leaderRepository.findByIdWithParish(id)
                .orElseThrow(() -> validation.notFound("Không tìm thấy Huynh Trưởng"));
        if (!Boolean.TRUE.equals(leader.getStatus()))
            throw validation.validationError("Huynh Trưởng phải đang hoạt động");
        if (leader.getBirthDate() == null || leader.getParish() == null) {
            throw validation.validationError("Huynh Trưởng phải có ngày sinh và giáo xứ để sinh username");
        }
        return leader;
    }

    private Diocese requireDioceseForUpdate(UUID id) {
        if (id == null) throw validation.validationError("dioceseId là bắt buộc với ADMIN_DIOCESE");
        return dioceseRepository.findByIdForUpdate(id).orElseThrow(() -> validation.notFound("Không tìm thấy giáo phận"));
    }

    private void requireSystemWideRoles(List<Role> roles) {
        for (Role role : roles) {
            if (!AccountManagementAuthorizationHelper.isSystemWideCustomRole(role)) {
                throw validation.validationError("Vai trò " + role.getRoleCode()
                        + " không thuộc quyền toàn hệ thống nên SUPER_ADMIN không được cấp tài khoản cho vai trò này");
            }
        }
    }

    private List<String> normalizeCreateRoleCodes(AccountCreateRequest request) {
        List<String> selectedRoles = normalizeRoleCodes(request.getRoleCodes());
        if (!selectedRoles.isEmpty()) {
            return selectedRoles;
        }
        String singleRole = validation.normalizeOptionalText(request.getRoleCode());
        return singleRole == null || singleRole.isBlank() ? List.of() : List.of(singleRole);
    }

    private String resolveCreatePrimaryRole(AccountCreateRequest request, List<String> roleCodes) {
        String primaryRoleCode = validation.normalizeOptionalText(request.getPrimaryRoleCode());
        if (primaryRoleCode == null || primaryRoleCode.isBlank()) {
            if (roleCodes.size() == 1) return roleCodes.get(0);
            throw validation.validationError("Vui lòng chọn vai trò chính");
        }
        if (!roleCodes.contains(primaryRoleCode)) {
            throw validation.validationError("Vai trò chính phải nằm trong danh sách vai trò đã chọn");
        }
        return primaryRoleCode;
    }

    private void requireLeaderInsideDiocese(Leader leader, UUID dioceseId) {
        UUID leaderDioceseId = leader.getParish().getDeanery().getDiocese().getId();
        if (!dioceseId.equals(leaderDioceseId)) {
            throw validation.validationError("Huynh Trưởng phải thuộc giáo phận của tài khoản ADMIN_DIOCESE");
        }
    }

    private String generateLeaderUsernameBase(Leader leader) {
        String base = normalize(leader.getFirstName()) + normalize(leader.getParish().getName())
                + leader.getBirthDate().getDayOfMonth() + leader.getBirthDate().getMonthValue();
        if (base.isBlank()) throw validation.validationError("Không thể sinh username từ thông tin Huynh Trưởng");
        return base;
    }

    private String usernameBaseFromEmail(String email) {
        int atIndex = email.indexOf('@');
        String localPart = atIndex > 0 ? email.substring(0, atIndex) : email;
        String base = normalize(localPart);
        if (base.isBlank()) {
            throw validation.validationError("Không thể sinh username từ credentialEmail");
        }
        return base;
    }

    private String nextAvailableUsername(String base) {
        if (base == null || base.isBlank()) {
            throw validation.validationError("Không thể sinh username từ thông tin tài khoản");
        }
        if (!accountRepository.existsByUsername(base)) return base;
        int suffix = 2;
        while (accountRepository.existsByUsername(base + suffix)) suffix++;
        return base + suffix;
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replace("đ", "d");
        return normalized.replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]", "");
    }

    private String generateTemporaryPassword() {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
        StringBuilder password = new StringBuilder(12);
        for (int index = 0; index < 12; index++) {
            password.append(alphabet[SECURE_RANDOM.nextInt(alphabet.length)]);
        }
        return password.toString();
    }

    private String requireCredentialEmail(String email) {
        String normalized = validation.normalizeRequiredText(email);
        if (normalized == null || normalized.isBlank()) {
            throw validation.validationError("Email nhận thông tin tài khoản là bắt buộc");
        }
        return normalized;
    }

    private String fullName(Leader leader) {
        String lastName = leader.getLastName() == null ? "" : leader.getLastName().trim();
        String firstName = leader.getFirstName() == null ? "" : leader.getFirstName().trim();
        return (lastName + " " + firstName).trim();
    }

    private void assignInitialRoles(Account account, List<Role> roles, String primaryRoleCode, String actor) {
        OffsetDateTime now = OffsetDateTime.now();
        for (Role role : roles) {
            AccountRole accountRole = new AccountRole();
            accountRole.setAccount(account);
            accountRole.setRole(role);
            accountRole.setIsPrimary(primaryRoleCode.equals(role.getRoleCode()));
            accountRole.setAssignedBy(actor);
            accountRole.setAssignedAt(now);
            accountRole.setStatus(true);
            accountRole.setCreatedAt(now);
            accountRole.setUpdatedAt(now);
            accountRole.setCreatedBy(actor);
            accountRole.setUpdatedBy(actor);
            accountRoleRepository.save(accountRole);
        }
    }

    /** Email liên hệ chỉ trả về cho SUPER_ADMIN; các cấp khác không thấy. */
    private AccountResponse response(Account account, Authentication authentication) {
        AccountResponse base = response(account);
        return isSuperAdmin(authentication) ? base.withEmail(emailResolver.resolve(account)) : base;
    }

    private AccountResponse response(Account account) {
        Leader leader = account.getLeader();
        List<AccountRole> activeRoles = activeRoleAssignments(account.getUsername());
        AccountUnits units = AccountUnits.of(account, leader);
        return AccountResponse.builder()
                .username(account.getUsername())
                .leaderId(leader == null ? null : leader.getId())
                .leaderHolyName(leader == null ? null : leader.getHolyName())
                .leaderFullName(buildFullName(leader))
                .leaderLevel(leader == null ? null : leader.getLeaderLevel())
                .leaderParishName(leader == null || leader.getParish() == null ? null : phucuong.ht.modules.parish.model.ParishUnitNames.of(leader.getParish()).parishName())
                .leaderSubUnitName(leader == null || leader.getParish() == null ? null : phucuong.ht.modules.parish.model.ParishUnitNames.of(leader.getParish()).subUnitName())
                .primaryRoleCode(primaryRoleCode(activeRoles))
                .primaryRoleName(primaryRoleName(activeRoles))
                .roleCodes(roleCodes(activeRoles))
                .roleNames(roleNames(activeRoles))
                .secondaryRoleNames(secondaryRoleNames(activeRoles))
                .dioceseId(units.dioceseId())
                .dioceseName(units.dioceseName())
                .deaneryId(units.deaneryId())
                .deaneryName(units.deaneryName())
                .parishId(units.parishId())
                .parishName(units.parishName())
                .authProvider(account.getAuthProvider())
                .providerId(account.getProviderId())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .createdBy(account.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(account.getCreatedBy()))
                .updatedAt(account.getUpdatedAt())
                .updatedBy(account.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(account.getUpdatedBy()))
                .build();
    }

    private List<String> normalizeRoleCodes(List<String> roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        return roleCodes.stream()
                .map(validation::normalizeOptionalText)
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private boolean isVisibleInAccountList(Authentication authentication, Account account) {
        if (isSuperAdmin(authentication)) {
            if (accountRoleRepository.findActiveRoleCodesByUsername(account.getUsername()).stream()
                    .anyMatch(SUPER_ADMIN_ACCOUNT_LIST_ROLES::contains)) {
                return true;
            }
            boolean unitless = account.getLeader() == null && account.getDiocese() == null
                    && account.getDeanery() == null && account.getParish() == null;
            return unitless && accountRoleRepository.findActiveByUsername(account.getUsername()).stream()
                    .anyMatch(accountRole -> AccountManagementAuthorizationHelper.isSystemWideCustomRole(accountRole.getRole()));
        }
        return authorizationHelper.canRead(authentication, account);
    }

    private boolean isValidAccountPermissionTarget(Account account) {
        List<AccountRole> activeRoles = accountRoleRepository.findActiveByUsername(account.getUsername());
        return activeRoles.stream().anyMatch(accountRole -> {
            Role role = accountRole.getRole();
            if (role == null) return false;
            if (ACCOUNT_PERMISSION_ADMIN_ROLES.contains(role.getRoleCode())) return true;
            return !Boolean.TRUE.equals(role.getIsSystem()) && "super-admin".equalsIgnoreCase(role.getCreatedBy());
        });
    }

    private boolean matchesUsernameSearch(AccountResponse response, String search) {
        if (search == null || search.isBlank()) return true;
        String keyword = search.toLowerCase(Locale.ROOT);
        // Tìm theo tên đăng nhập, tên thánh / họ tên huynh trưởng, vai trò, giáo xứ, giáo hạt.
        java.util.List<String> haystack = new java.util.ArrayList<>(java.util.Arrays.asList(
                response.getUsername(), response.getLeaderHolyName(), response.getLeaderFullName(),
                response.getParishName(), response.getDeaneryName()));
        if (response.getRoleNames() != null) {
            haystack.addAll(response.getRoleNames());
        }
        return haystack.stream().anyMatch(text -> text != null && text.toLowerCase(Locale.ROOT).contains(keyword));
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUserDetails principal
                && principal.getRoles().contains(SUPER_ADMIN);
    }

    private boolean matchesAccountFilters(AccountResponse response, BaseSearchRequest request) {
        if (request.getStatus() != null && !request.getStatus().equals(response.getStatus())) return false;
        String search = request.getSearch() == null ? "" : request.getSearch().trim().toLowerCase(Locale.ROOT);
        if (!search.isBlank()) {
            String haystack = String.join(" ",
                    safe(response.getUsername()),
                    safe(response.getLeaderFullName()),
                    safe(response.getPrimaryRoleCode()),
                    safe(response.getDioceseName())
            ).toLowerCase(Locale.ROOT);
            if (!haystack.contains(search)) return false;
        }

        Map<String, Object> filters = request.getFilters() == null ? new LinkedHashMap<>() : request.getFilters();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            if (value.isBlank()) continue;
            switch (entry.getKey()) {
                case "primaryRoleCode", "roleCode", "role.roleCode" -> {
                    if (response.getRoleCodes() == null || !response.getRoleCodes().contains(value)) return false;
                }
                case "diocese.id", "dioceseId" -> {
                    if (response.getDioceseId() == null || !value.equals(response.getDioceseId().toString()))
                        return false;
                }
                case "deanery.id", "deaneryId" -> {
                    if (response.getDeaneryId() == null || !value.equals(response.getDeaneryId().toString()))
                        return false;
                }
                case "parish.id", "parishId" -> {
                    if (response.getParishId() == null || !value.equals(response.getParishId().toString()))
                        return false;
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private Page<AccountResponse> page(BaseSearchRequest request, List<AccountResponse> accounts) {
        return InMemoryPager.sortedPage(request, accounts);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<AccountRole> activeRoleAssignments(String username) {
        return accountRoleRepository.findActiveByUsername(username).stream()
                .sorted(Comparator.comparing((AccountRole accountRole) -> !Boolean.TRUE.equals(accountRole.getIsPrimary()))
                        .thenComparing(AccountRole::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String primaryRoleCode(List<AccountRole> activeRoles) {
        return activeRoles.stream()
                .map(accountRole -> accountRole.getRole() == null ? null : accountRole.getRole().getRoleCode())
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String primaryRoleName(List<AccountRole> activeRoles) {
        return activeRoles.stream()
                .map(this::roleDisplayName)
                .filter(roleName -> roleName != null && !roleName.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<String> roleCodes(List<AccountRole> activeRoles) {
        return activeRoles.stream()
                .map(accountRole -> accountRole.getRole() == null ? null : accountRole.getRole().getRoleCode())
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .toList();
    }

    private List<String> roleNames(List<AccountRole> activeRoles) {
        return activeRoles.stream()
                .map(this::roleDisplayName)
                .filter(roleName -> roleName != null && !roleName.isBlank())
                .toList();
    }

    private List<String> secondaryRoleNames(List<AccountRole> activeRoles) {
        return roleNames(activeRoles).stream().skip(1).toList();
    }

    private String roleDisplayName(AccountRole accountRole) {
        if (accountRole == null || accountRole.getRole() == null) return null;
        Role role = accountRole.getRole();
        return role.getRoleName() == null || role.getRoleName().isBlank() ? role.getRoleCode() : role.getRoleName();
    }

    private String buildFullName(Leader leader) {
        if (leader == null) {
            return null;
        }
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }
}
