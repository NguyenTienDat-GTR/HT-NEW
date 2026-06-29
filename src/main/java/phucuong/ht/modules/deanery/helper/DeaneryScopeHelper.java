package phucuong.ht.modules.deanery.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeaneryScopeHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final DeaneryRepository deaneryRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void enforceCreateScope(DeaneryCreateRequest request, Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_ALL)) {
            return;
        }

        if (!securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền tạo giáo hạt");
        }

        UUID activeDioceseId = getRequiredActiveDioceseId(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE);
        if (!activeDioceseId.equals(request.getDioceseId())) {
            throw commonValidationHelper.forbidden("Không thể tạo giáo hạt ngoài phạm vi giáo phận hiện tại");
        }
    }

    public void enforceDeleteScope(Authentication authentication, Deanery deanery) {
        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_ALL)) {
            return;
        }

        if (!securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_DIOCESE)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xóa giáo hạt");
        }

        UUID activeDioceseId = getRequiredActiveDioceseId(authentication, DeaneryAccessEvaluator.DELETE_DIOCESE);
        UUID targetDioceseId = deaneryRepository.findDioceseIdByDeaneryId(deanery.getId())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt: " + deanery.getId()));

        if (!activeDioceseId.equals(targetDioceseId)) {
            throw commonValidationHelper.forbidden("Không thể xóa giáo hạt ngoài phạm vi giáo phận hiện tại");
        }
    }

    public UUID getRequiredActiveDioceseId(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + permissionCode);
        }

        return accountRepository.findActiveDioceseIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tài khoản hiện tại không có phạm vi giáo phận hợp lệ cho quyền " + permissionCode
                ));
    }

    public UUID getRequiredActiveDeaneryId(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + permissionCode);
        }

        return accountRepository.findActiveDeaneryIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tài khoản hiện tại không có phạm vi giáo hạt hợp lệ cho quyền " + permissionCode
                ));
    }
}
