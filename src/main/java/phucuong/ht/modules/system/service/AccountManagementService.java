package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.AuthProvider;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.modules.system.model.request.AccountUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountResponse;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private static final String ADMIN_DIOCESE = "ADMIN_DIOCESE";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    @Transactional
    public AccountResponse create(AccountCreateRequest request, Authentication authentication) {
        Role role = roleRepository.findByRoleCodeAndStatusTrue(request.getRoleCode())
                .orElseThrow(() -> validation.notFound("Không tìm thấy role " + request.getRoleCode()));
        boolean dioceseAdmin = ADMIN_DIOCESE.equals(role.getRoleCode());

        Account account = new Account();
        Leader credentialLeader = null;
        String recipientEmail;
        String recipientName;
        String usernameBase;
        if (dioceseAdmin) {
            Diocese diocese = requireDioceseForUpdate(request.getDioceseId());
            account.setDiocese(diocese);
            if (accountRepository.existsAdminDioceseByDioceseId(diocese.getId())) {
                throw validation.conflict("Mỗi giáo phận chỉ được có một account ADMIN_DIOCESE");
            }
            if (request.getLeaderId() != null) {
                credentialLeader = requireLeader(request.getLeaderId(), "Không tìm thấy leader");
                requireLeaderInsideDiocese(credentialLeader, diocese.getId());
                account.setLeader(credentialLeader);
                recipientEmail = requireCredentialEmail(credentialLeader.getEmail());
                recipientName = fullName(credentialLeader);
                usernameBase = generateLeaderUsernameBase(credentialLeader);
                if (request.getCredentialEmail() != null) {
                    throw validation.validationError("credentialEmail chỉ dùng khi ADMIN_DIOCESE không gắn leader");
                }
            } else {
                recipientEmail = requireCredentialEmail(request.getCredentialEmail());
                recipientName = "Quản trị viên " + diocese.getName();
                usernameBase = normalize(diocese.getName()) + "admin";
            }
        } else {
            if (request.getDioceseId() != null || request.getCredentialEmail() != null) {
                throw validation.validationError("Account không phải ADMIN_DIOCESE không dùng dioceseId hoặc credentialEmail");
            }
            credentialLeader = requireLeader(request.getLeaderId(), "leaderId là bắt buộc với account không phải ADMIN_DIOCESE");
            account.setLeader(credentialLeader);
            recipientEmail = requireCredentialEmail(credentialLeader.getEmail());
            recipientName = fullName(credentialLeader);
            usernameBase = generateLeaderUsernameBase(credentialLeader);
        }

        authorizationHelper.requireCanCreate(authentication, account, role);
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
        Account saved = accountRepository.save(account);
        assignInitialRole(saved, role, actor);
        mailService.sendInitialCredentials(recipientEmail, recipientName, username, rawPassword);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> findAll(Pageable pageable, Authentication authentication) {
        var manageable = accountRepository.findAll().stream()
                .filter(account -> authorizationHelper.canRead(authentication, account))
                .toList();
        long offset = pageable.getOffset();
        int start = offset >= manageable.size() ? manageable.size() : (int) offset;
        int end = Math.min(start + pageable.getPageSize(), manageable.size());
        return new PageImpl<>(manageable.subList(start, end).stream().map(this::response).toList(), pageable, manageable.size());
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(String username, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanRead(authentication, account);
        return response(account);
    }

    @Transactional
    public AccountResponse update(String username, AccountUpdateRequest request, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanManage(authentication, account);
        boolean dioceseAdmin = isAdminDiocese(account);
        if (dioceseAdmin) {
            Diocese targetDiocese = account.getDiocese();
            if (request.getDioceseId() != null) {
                targetDiocese = requireDioceseForUpdate(request.getDioceseId());
                if (accountRepository.existsOtherAdminDiocese(targetDiocese.getId(), username)) {
                    throw validation.conflict("Giáo phận đích đã có account ADMIN_DIOCESE");
                }
                account.setDiocese(targetDiocese);
            }
            if (request.getLeaderId() != null) {
                Leader leader = requireLeader(request.getLeaderId(), "Không tìm thấy leader");
                requireLeaderInsideDiocese(leader, targetDiocese.getId());
                account.setLeader(leader);
            } else if (account.getLeader() != null) {
                requireLeaderInsideDiocese(account.getLeader(), targetDiocese.getId());
            }
        } else {
            if (request.getDioceseId() != null) {
                throw validation.validationError("Chỉ account ADMIN_DIOCESE không gắn leader mới có direct dioceseId");
            }
            if (request.getLeaderId() != null) account.setLeader(requireLeader(request.getLeaderId(), "Không tìm thấy leader"));
            if (account.getLeader() == null) throw validation.validationError("Account bắt buộc phải gắn với leader");
        }
        authorizationHelper.requireCanManage(authentication, account);
        account.setUpdatedBy(validation.requireAuditActor(authentication, "Không xác định được người cập nhật tài khoản"));
        redisTokenSessionService.revokeAllSessions(account.getUsername());
        return response(account);
    }

    @Transactional
    public void delete(String username, Authentication authentication) {
        Account account = require(username);
        authorizationHelper.requireCanDelete(authentication, account);
        redisTokenSessionService.revokeAllSessions(account.getUsername());
        accountRepository.delete(account);
    }

    @Transactional
    public AccountResponse toggleStatus(String username, StatusToggleRequest request, Authentication authentication) {
        validation.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        Account account = require(username);
        authorizationHelper.requireCanManage(authentication, account);
        account.setStatus(!Boolean.TRUE.equals(account.getStatus()));
        account.setUpdatedBy(validation.requireAuditActor(authentication, "Không xác định được người đổi trạng thái tài khoản"));
        redisTokenSessionService.revokeAllSessions(account.getUsername());
        return response(account);
    }

    private Account require(String username) {
        return accountRepository.findById(username)
                .orElseThrow(() -> validation.notFound("Không tìm thấy tài khoản: " + username));
    }

    private Leader requireLeader(UUID id, String missingMessage) {
        if (id == null) throw validation.validationError(missingMessage);
        Leader leader = leaderRepository.findByIdWithParish(id)
                .orElseThrow(() -> validation.notFound("Không tìm thấy leader"));
        if (!Boolean.TRUE.equals(leader.getStatus())) throw validation.validationError("Leader phải đang hoạt động");
        if (leader.getBirthDate() == null || leader.getParish() == null) {
            throw validation.validationError("Leader phải có ngày sinh và giáo xứ để sinh username");
        }
        return leader;
    }

    private Diocese requireDioceseForUpdate(UUID id) {
        if (id == null) throw validation.validationError("dioceseId là bắt buộc với ADMIN_DIOCESE");
        return dioceseRepository.findByIdForUpdate(id).orElseThrow(() -> validation.notFound("Không tìm thấy giáo phận"));
    }

    private void requireLeaderInsideDiocese(Leader leader, UUID dioceseId) {
        UUID leaderDioceseId = leader.getParish().getDeanery().getDiocese().getId();
        if (!dioceseId.equals(leaderDioceseId)) {
            throw validation.validationError("Leader phải thuộc giáo phận của account ADMIN_DIOCESE");
        }
    }

    private String generateLeaderUsernameBase(Leader leader) {
        String base = normalize(leader.getFirstName()) + normalize(leader.getParish().getName())
                + leader.getBirthDate().getDayOfMonth() + leader.getBirthDate().getMonthValue();
        if (base.isBlank()) throw validation.validationError("Không thể sinh username từ thông tin leader");
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

    private boolean isAdminDiocese(Account account) {
        return accountRoleRepository.findActiveRoleCodesByUsername(account.getUsername()).stream()
                .allMatch(ADMIN_DIOCESE::equals)
                && !accountRoleRepository.findActiveRoleCodesByUsername(account.getUsername()).isEmpty();
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

    private AccountResponse response(Account account) {
        return AccountResponse.builder()
                .username(account.getUsername())
                .leaderId(account.getLeader() == null ? null : account.getLeader().getId())
                .dioceseId(account.getDiocese() == null ? null : account.getDiocese().getId())
                .deaneryId(null)
                .parishId(null)
                .authProvider(account.getAuthProvider())
                .providerId(account.getProviderId())
                .status(account.getStatus())
                .build();
    }
}
