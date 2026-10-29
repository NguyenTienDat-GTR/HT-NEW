package phucuong.ht.modules.leader.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeaderScopeHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final ParishRepository parishRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void enforceCreateScope(LeaderCreateRequest request, Authentication authentication) {
        if (request == null || request.getParishId() == null) {
            throw commonValidationHelper.validationError("parishId là bắt buộc khi tạo Huynh Trưởng");
        }
        if (!hasAnyCreateAuthority(authentication)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền tạo Huynh Trưởng");
        }

        String username = requireUsername(authentication);
        UUID targetParishId = request.getParishId();
        if (securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_DIOCESE)) {
            UUID actorScopeId = accountRepository.findActiveDioceseIdByUsername(username)
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản không có phạm vi giáo phận hợp lệ"));
            UUID targetScopeId = parishRepository.findDioceseIdByParishId(targetParishId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ"));
            requireSameScope(actorScopeId, targetScopeId, "giáo phận");
            return;
        }
        if (securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_DEANERY)) {
            UUID actorScopeId = accountRepository.findActiveDeaneryIdByUsername(username)
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản không có phạm vi giáo hạt hợp lệ"));
            UUID targetScopeId = parishRepository.findDeaneryIdByParishId(targetParishId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ"));
            requireSameScope(actorScopeId, targetScopeId, "giáo hạt");
            return;
        }
        requireSameScope(
                getRequiredActiveParishId(authentication, LeaderAccessEvaluator.CREATE_PARISH),
                targetParishId,
                "giáo xứ"
        );
    }

    public void enforceUpdateParishScope(Authentication authentication, Leader existingLeader, UUID requestedParishId) {
        if (requestedParishId == null || existingLeader == null || existingLeader.getParish() == null) return;
        // Quản trị giáo phận / giáo hạt được chuyển huynh trưởng sang giáo xứ khác nhưng phải còn trong phạm vi của mình.
        if (securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.UPDATE_DIOCESE)) {
            UUID actorScopeId = getRequiredActiveDioceseId(authentication, LeaderAccessEvaluator.UPDATE_DIOCESE);
            UUID targetScopeId = parishRepository.findDioceseIdByParishId(requestedParishId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ"));
            requireSameScope(actorScopeId, targetScopeId, "giáo phận");
            return;
        }
        if (securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.UPDATE_DEANERY)) {
            UUID actorScopeId = getRequiredActiveDeaneryId(authentication, LeaderAccessEvaluator.UPDATE_DEANERY);
            UUID targetScopeId = parishRepository.findDeaneryIdByParishId(requestedParishId)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ"));
            requireSameScope(actorScopeId, targetScopeId, "giáo hạt");
            return;
        }
        UUID activeParishId = getRequiredActiveParishId(authentication, LeaderAccessEvaluator.UPDATE_PARISH);
        UUID currentParishId = existingLeader.getParish().getId();
        if (!activeParishId.equals(currentParishId) || !activeParishId.equals(requestedParishId)) {
            throw commonValidationHelper.forbidden("Không thể chuyển Huynh Trưởng ra ngoài giáo xứ hiện tại");
        }
    }

    public UUID getRequiredActiveParishId(Authentication authentication, String permissionCode) {
        String username = requireUsername(authentication);
        return accountRepository.findActiveParishIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tài khoản hiện tại không có phạm vi giáo xứ hợp lệ cho quyền " + permissionCode));
    }

    public UUID getRequiredActiveDioceseId(Authentication authentication, String permissionCode) {
        String username = requireUsername(authentication);
        return accountRepository.findActiveDioceseIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tài khoản hiện tại không có phạm vi giáo phận hợp lệ cho quyền " + permissionCode));
    }

    public UUID getRequiredActiveDeaneryId(Authentication authentication, String permissionCode) {
        String username = requireUsername(authentication);
        return accountRepository.findActiveDeaneryIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden(
                        "Tài khoản hiện tại không có phạm vi giáo hạt hợp lệ cho quyền " + permissionCode));
    }

    private boolean hasAnyCreateAuthority(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_DIOCESE)
                || securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_DEANERY)
                || securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_PARISH);
    }

    private String requireUsername(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        return username;
    }

    private void requireSameScope(UUID actorScopeId, UUID targetScopeId, String scopeName) {
        if (!actorScopeId.equals(targetScopeId)) {
            throw commonValidationHelper.forbidden("Không thể tạo Huynh Trưởng ngoài phạm vi " + scopeName + " hiện tại");
        }
    }
}
