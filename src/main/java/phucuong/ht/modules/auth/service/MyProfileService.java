package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.response.MyProfileResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.certificate.config.model.response.CertificatePreviewResponse;
import phucuong.ht.modules.certificate.config.service.CertificateConfigService;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.service.CertificateService;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Hồ sơ của chính người đang đăng nhập; không cần quyền đọc danh sách Huynh Trưởng hay chứng nhận. */
@Service
@RequiredArgsConstructor
public class MyProfileService {

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final LeaderService leaderService;
    private final CertificateService certificateService;
    private final CertificateConfigService certificateConfigService;
    private final AccountEmailResolver emailResolver;

    @Transactional(readOnly = true)
    public MyProfileResponse currentProfile(Authentication authentication) {
        Account account = requireAccount(authentication);
        UUID leaderId = account.getLeader() == null ? null : account.getLeader().getId();
        LeaderResponse leader = leaderId == null ? null : leaderService.findById(leaderId, authentication);

        return MyProfileResponse.builder()
                .username(account.getUsername())
                .roles(roles(account.getUsername()))
                .email(emailResolver.resolve(account))
                .hasLeader(leader != null)
                .leader(leader)
                .rankHistory(leaderId == null ? List.of() : leaderService.findRankHistory(leaderId))
                .certificates(leaderId == null ? List.of() : certificateService.findByLeader(leaderId, authentication))
                .build();
    }

    /** Chi tiết một chứng nhận của chính mình; từ chối nếu chứng nhận không thuộc Huynh Trưởng của tài khoản. */
    @Transactional(readOnly = true)
    public CertificateResponse myCertificate(String certificateCode, Authentication authentication) {
        requireOwnCertificate(certificateCode, authentication);
        return certificateService.findById(certificateCode, authentication);
    }

    /** Bản xem trước chứng nhận của chính mình. */
    public CertificatePreviewResponse myCertificatePreview(String certificateCode, Authentication authentication) {
        requireOwnCertificate(certificateCode, authentication);
        return certificateConfigService.preview(certificateCode);
    }

    private void requireOwnCertificate(String certificateCode, Authentication authentication) {
        Account account = requireAccount(authentication);
        boolean owned = account.getLeader() != null && StringUtils.hasText(certificateCode)
                && certificateService.findByLeader(account.getLeader().getId(), authentication).stream()
                .anyMatch(certificate -> certificateCode.equals(certificate.getCertificateCode()));
        if (!owned) {
            throw new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chứng nhận của bạn");
        }
    }

    private List<MyProfileResponse.RoleItem> roles(String username) {
        return accountRoleRepository.findActiveByUsername(username).stream()
                .sorted(Comparator
                        .comparing((AccountRole role) -> !Boolean.TRUE.equals(role.getIsPrimary()))
                        .thenComparing(role -> role.getRole().getDisplayOrder() == null ? Integer.MAX_VALUE : role.getRole().getDisplayOrder()))
                .map(role -> MyProfileResponse.RoleItem.builder()
                        .code(role.getRole().getRoleCode())
                        .name(StringUtils.hasText(role.getRole().getRoleName()) ? role.getRole().getRoleName() : role.getRole().getRoleCode())
                        .primary(Boolean.TRUE.equals(role.getIsPrimary()))
                        .build())
                .toList();
    }

    private Account requireAccount(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Yêu cầu xác thực");
        }
        return accountRepository.findActiveByUsername(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));
    }
}
