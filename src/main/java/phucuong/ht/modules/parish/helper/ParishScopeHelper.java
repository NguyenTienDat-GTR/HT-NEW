package phucuong.ht.modules.parish.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.parish.security.ParishAccessEvaluator;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ParishScopeHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void enforceCreateScope(ParishCreateRequest request, Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_ALL)) {
            return;
        }

        UUID targetDeaneryId = request.getDeaneryId();
        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_DIOCESE)) {
            UUID activeDioceseId = getRequiredActiveDioceseId(authentication, ParishAccessEvaluator.CREATE_DIOCESE);
            UUID targetDioceseId = deaneryRepository.findDioceseIdByDeaneryId(targetDeaneryId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt: " + targetDeaneryId));
            if (!activeDioceseId.equals(targetDioceseId)) {
                throw commonValidationHelper.forbidden("Không thể tạo giáo xứ ngoài phạm vi giáo phận hiện tại");
            }
            return;
        }

        if (!securityUtils.hasAuthority(authentication, ParishAccessEvaluator.CREATE_DEANERY)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền tạo giáo xứ");
        }

        UUID activeDeaneryId = getRequiredActiveDeaneryId(authentication, ParishAccessEvaluator.CREATE_DEANERY);
        if (!activeDeaneryId.equals(targetDeaneryId)) {
            throw commonValidationHelper.forbidden("Không thể tạo giáo xứ ngoài phạm vi giáo hạt hiện tại");
        }
    }

    public void enforceDeleteScope(Authentication authentication, Parish parish) {
        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.DELETE_ALL)) {
            return;
        }

        UUID parishId = parish.getId();
        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.DELETE_DIOCESE)) {
            UUID activeDioceseId = getRequiredActiveDioceseId(authentication, ParishAccessEvaluator.DELETE_DIOCESE);
            UUID targetDioceseId = parishRepository.findDioceseIdByParishId(parishId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ: " + parishId));
            if (!activeDioceseId.equals(targetDioceseId)) {
                throw commonValidationHelper.forbidden("Không thể xóa giáo xứ ngoài phạm vi giáo phận hiện tại");
            }
            return;
        }

        if (!securityUtils.hasAuthority(authentication, ParishAccessEvaluator.DELETE_DEANERY)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xóa giáo xứ");
        }

        UUID activeDeaneryId = getRequiredActiveDeaneryId(authentication, ParishAccessEvaluator.DELETE_DEANERY);
        UUID targetDeaneryId = parishRepository.findDeaneryIdByParishId(parishId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ: " + parishId));
        if (!activeDeaneryId.equals(targetDeaneryId)) {
            throw commonValidationHelper.forbidden("Không thể xóa giáo xứ ngoài phạm vi giáo hạt hiện tại");
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

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_PARISH)) {
            throw commonValidationHelper.forbidden("Tài khoản giáo xứ không được thay đổi giáo hạt của giáo xứ");
        }

        UUID currentDioceseId = existingParish.getDeanery().getDiocese() == null ? null : existingParish.getDeanery().getDiocese().getId();
        UUID targetDioceseId = targetDeanery.getDiocese() == null ? null : targetDeanery.getDiocese().getId();
        if (currentDioceseId == null || !currentDioceseId.equals(targetDioceseId)) {
            throw commonValidationHelper.validationError("Chỉ được chuyển giáo xứ sang giáo hạt cùng giáo phận");
        }

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_ALL)
                || securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_DIOCESE)) {
            return;
        }

        if (securityUtils.hasAuthority(authentication, ParishAccessEvaluator.UPDATE_DEANERY)) {
            UUID activeDeaneryId = getRequiredActiveDeaneryId(authentication, ParishAccessEvaluator.UPDATE_DEANERY);
            if (!activeDeaneryId.equals(currentDeaneryId) || !activeDeaneryId.equals(targetDeaneryId)) {
                throw commonValidationHelper.forbidden("Không thể chuyển giáo xứ ra ngoài giáo hạt hiện tại");
            }
            return;
        }

        throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền thay đổi giáo hạt của giáo xứ");
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

    public UUID getRequiredActiveParishId(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + permissionCode);
        }

        return accountRepository.findActiveParishIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tài khoản hiện tại không có phạm vi giáo xứ hợp lệ cho quyền " + permissionCode
                ));
    }
}
