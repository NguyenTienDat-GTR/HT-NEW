package phucuong.ht.modules.parish.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.security.ParishAccessEvaluator;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ParishScopeHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void enforceCreateScope(ParishCreateRequest request, Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_ALL)) {
            return;
        }

        if (!securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_DEANERY)) {
            throw commonValidationHelper.forbidden("Tai khoan hien tai khong co quyen tao giao xu");
        }

        UUID activeDeaneryId = getRequiredActiveDeaneryId(authentication, ParishAccessEvaluator.CREATE_DEANERY);
        if (!activeDeaneryId.equals(request.getDeaneryId())) {
            throw commonValidationHelper.forbidden("Khong the tao giao xu ngoai pham vi giao hat hien tai");
        }
    }

    public void enforceDeaneryChangeScope(Authentication authentication, Parish existingParish, Deanery targetDeanery) {
        if (existingParish == null || existingParish.getDeanery() == null || targetDeanery == null) {
            return;
        }

        UUID currentDeaneryId = existingParish.getDeanery().getId();
        UUID targetDeaneryId = targetDeanery.getId();
        if (currentDeaneryId == null || targetDeaneryId == null || currentDeaneryId.equals(targetDeaneryId)) {
            return;
        }

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_ALL)) {
            return;
        }

        throw commonValidationHelper.forbidden("Tai khoan hien tai khong co quyen thay doi giao hat cua giao xu");
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

    public UUID getRequiredActiveParishId(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Tai khoan hien tai khong xac dinh duoc ten dang nhap cho quyen " + permissionCode);
        }

        return accountRepository.findActiveParishIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tai khoan hien tai khong co pham vi giao xu hop le cho quyen " + permissionCode
                ));
    }
}
