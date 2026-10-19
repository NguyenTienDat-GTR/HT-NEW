package phucuong.ht.common.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountScopeValidationHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper commonValidationHelper;

    public String requireCurrentUsername(Authentication authentication, String message) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden(message);
        }
        return username;
    }

    // Tài khoản gắn trực tiếp với giáo phận/giáo hạt (khác tài khoản giáo xứ suy ra phạm vi lên cấp trên).
    public boolean hasDirectDiocese(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        return StringUtils.hasText(username) && accountRepository.findDirectActiveDioceseIdByUsername(username).isPresent();
    }

    public boolean hasDirectDeanery(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        return StringUtils.hasText(username) && accountRepository.findDirectActiveDeaneryIdByUsername(username).isPresent();
    }

    public UUID requireActiveDioceseId(Authentication authentication, String permissionCode) {
        String username = requireCurrentUsername(authentication, "Không xác định được tài khoản hiện tại cho quyền " + permissionCode);
        return accountRepository.findActiveDioceseIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi giáo phận hợp lệ cho quyền " + permissionCode));
    }

    public UUID requireActiveDeaneryId(Authentication authentication, String permissionCode) {
        String username = requireCurrentUsername(authentication, "Không xác định được tài khoản hiện tại cho quyền " + permissionCode);
        return accountRepository.findActiveDeaneryIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi giáo hạt hợp lệ cho quyền " + permissionCode));
    }

    public UUID requireActiveParishId(Authentication authentication, String permissionCode) {
        String username = requireCurrentUsername(authentication, "Không xác định được tài khoản hiện tại cho quyền " + permissionCode);
        return accountRepository.findActiveParishIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi giáo xứ hợp lệ cho quyền " + permissionCode));
    }
}
