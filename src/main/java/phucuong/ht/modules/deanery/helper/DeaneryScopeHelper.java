package phucuong.ht.modules.deanery.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeaneryScopeHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void enforceCreateScope(DeaneryCreateRequest request, Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_ALL)) {
            return;
        }

        if (!securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE)) {
            throw commonValidationHelper.forbidden("Tai khoan hien tai khong co quyen tao giao hat");
        }

        UUID activeDioceseId = getRequiredActiveDioceseId(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE);
        if (!activeDioceseId.equals(request.getDioceseId())) {
            throw commonValidationHelper.forbidden("Khong the tao giao hat ngoai pham vi giao phan hien tai");
        }
    }

    public UUID getRequiredActiveDioceseId(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Tai khoan hien tai khong xac dinh duoc ten dang nhap cho quyen " + permissionCode);
        }

        return accountRepository.findActiveDioceseIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tai khoan hien tai khong co pham vi giao phan hop le cho quyen " + permissionCode
                ));
    }

    public UUID getRequiredActiveDeaneryId(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Tai khoan hien tai khong xac dinh duoc ten dang nhap cho quyen " + permissionCode);
        }

        return accountRepository.findActiveDeaneryIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tai khoan hien tai khong co pham vi giao hat hop le cho quyen " + permissionCode
                ));
    }
}
