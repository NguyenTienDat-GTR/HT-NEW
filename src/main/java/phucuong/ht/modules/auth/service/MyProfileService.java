package phucuong.ht.modules.auth.service;

import java.time.Duration;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.modules.auth.model.request.MyAvatarUpdateRequest;
import phucuong.ht.modules.auth.model.request.MyProfileUpdateRequest;
import phucuong.ht.modules.leader.model.request.LeaderSelfUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.response.MyProfileResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.certificate.config.model.response.CertificatePreviewResponse;
import phucuong.ht.modules.certificate.config.service.CertificateConfigService;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.service.CertificateService;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;

import java.time.LocalDate;
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
    private final PasswordEncoder passwordEncoder;
    private final phucuong.ht.modules.leader.repository.LeaderRepository leaderRepository;
    private final LeaderAccessEvaluator leaderAccessEvaluator;
    private final PasswordOtpStore otpStore;
    private final phucuong.ht.common.utils.securityUtils securityUtils;
    private final phucuong.ht.modules.media.service.MediaAssetService mediaAssetService;
    private final phucuong.ht.modules.media.service.MediaUrls mediaUrls;

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
                .canEditBirthDate(leaderId != null && leaderAccessEvaluator.canUpdateBirthDate(authentication, leaderId))
                .imageUrl(mediaUrls.display(account.getImageAssetId(), MediaCategory.ACCOUNT_AVATAR))
                .imageThumbUrl(mediaUrls.small(account.getImageAssetId(), MediaCategory.ACCOUNT_AVATAR))
                .imageAssetId(account.getImageAssetId())
                .leader(leader)
                .rankHistory(leaderId == null ? List.of() : leaderService.findRankHistory(leaderId))
                .certificates(leaderId == null ? List.of() : certificateService.findByLeader(leaderId, authentication))
                .build();
    }

    /**
     * Tự sửa thông tin cá nhân của Huynh Trưởng gắn với tài khoản. Chỉ sửa được các trường như khi quản trị sửa hồ sơ huynh trưởng
     * (tên thánh, giới tính, email, số điện thoại, ảnh), không gồm họ và tên / giáo xứ / giáo hạt / cấp độ.
     * Ngày sinh chỉ đổi được khi tài khoản có quyền sửa ngày sinh đối với hồ sơ của mình (cùng điều kiện với PATCH /api/leaders/{id}/birth-date).
     * Đổi email phải nhập đúng mật khẩu hiện tại vì email nhận OTP đổi / quên mật khẩu; sai nhiều lần bị khóa tạm.
     */
    public MyProfileResponse updateProfile(MyProfileUpdateRequest request, Authentication authentication) {
        Account account = requireAccount(authentication);
        if (account.getLeader() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Tài khoản này không gắn hồ sơ Huynh Trưởng nên không có thông tin cá nhân để sửa");
        }
        UUID leaderId = account.getLeader().getId();
        String requestedEmail = request.getEmail() == null ? null : request.getEmail().trim();
        // Đọc giá trị hiện tại bằng truy vấn riêng: quan hệ leader của account là lazy và hàm này không chạy trong giao dịch.
        Leader current = leaderRepository.findById(leaderId).orElse(null);
        String currentEmail = current == null ? null : current.getEmail();
        LocalDate currentBirthDate = current == null ? null : current.getBirthDate();
        // Gửi lại đúng ngày sinh hiện tại không phải là thay đổi nên không đòi quyền.
        boolean birthDateChanging = request.getBirthDate() != null && !request.getBirthDate().equals(currentBirthDate);
        if (birthDateChanging && !leaderAccessEvaluator.canUpdateBirthDate(authentication, leaderId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản của bạn không có quyền sửa ngày sinh");
        }
        if (StringUtils.hasText(requestedEmail) && !requestedEmail.equals(currentEmail)) {
            requireCurrentPassword(account, request.getCurrentPassword());
        }

        LeaderSelfUpdateRequest changes = new LeaderSelfUpdateRequest();
        changes.setHolyName(request.getHolyName());
        changes.setBirthDate(birthDateChanging ? request.getBirthDate() : null);
        changes.setGender(request.getGender());
        changes.setEmail(request.getEmail());
        changes.setPhoneNumber(request.getPhoneNumber());
        changes.setImageAssetId(request.getImageAssetId());
        changes.setClearImage(request.getClearImage());
        leaderService.updateOwnProfile(leaderId, changes, securityUtils.currentAuditActor(authentication));
        return currentProfile(authentication);
    }

    /**
     * Đặt ảnh đại diện cho tài khoản KHÔNG gắn huynh trưởng (tài khoản gắn huynh trưởng đổi ảnh trong hồ sơ huynh trưởng).
     * Chỉ thay được, không xóa. Ảnh nằm trong thư mục giáo phận của tài khoản, hoặc thư mục hệ thống nếu tài khoản không thuộc giáo phận nào.
     */
    public MyProfileResponse updateAvatar(MyAvatarUpdateRequest request, Authentication authentication) {
        Account account = requireAccount(authentication);
        if (account.getLeader() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Tài khoản gắn hồ sơ Huynh Trưởng đổi ảnh đại diện bằng chức năng sửa hồ sơ cá nhân");
        }
        UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(account.getUsername()).orElse(null);
        UUID oldAssetId = account.getImageAssetId();
        UUID newAssetId = mediaAssetService.attach(request.getImageAssetId(), MediaCategory.ACCOUNT_AVATAR, dioceseId,
                securityUtils.currentAuditActor(authentication));
        accountRepository.setImageAsset(account.getUsername(), newAssetId);
        if (oldAssetId != null && !oldAssetId.equals(newAssetId)) {
            mediaAssetService.release(oldAssetId);
        }
        return currentProfile(authentication);
    }

    private void requireCurrentPassword(Account account, String currentPassword) {
        String username = account.getUsername();
        if (otpStore.oldPasswordFailures(username) >= PasswordChangeService.MAX_OLD_PASSWORD_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode.VALIDATION_ERROR,
                    "Bạn đã nhập sai mật khẩu hiện tại quá nhiều lần, vui lòng thử lại sau 15 phút");
        }
        if (!StringUtils.hasText(currentPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Vui lòng nhập mật khẩu hiện tại để đổi email");
        }
        if (!passwordEncoder.matches(currentPassword, account.getPassword())) {
            otpStore.recordOldPasswordFailure(username, Duration.ofMinutes(15));
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu hiện tại không chính xác");
        }
        otpStore.clearOldPasswordFailures(username);
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
