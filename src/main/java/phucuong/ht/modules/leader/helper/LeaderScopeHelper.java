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

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeaderScopeHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void enforceCreateScope(LeaderCreateRequest request, Authentication authentication) {
        if (!securityUtils.hasAuthority(authentication, LeaderAccessEvaluator.CREATE_PARISH)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền tạo leader");
        }

        UUID activeParishId = getRequiredActiveParishId(authentication, LeaderAccessEvaluator.CREATE_PARISH);
        if (!activeParishId.equals(request.getParishId())) {
            throw commonValidationHelper.forbidden("Không thể tạo leader ngoài phạm vi giáo xứ hiện tại");
        }
    }

    public void enforceUpdateParishScope(Authentication authentication, Leader existingLeader, UUID requestedParishId) {
        if (requestedParishId == null || existingLeader == null || existingLeader.getParish() == null) {
            return;
        }

        UUID activeParishId = getRequiredActiveParishId(authentication, LeaderAccessEvaluator.UPDATE_PARISH);
        UUID currentParishId = existingLeader.getParish().getId();
        if (!activeParishId.equals(currentParishId) || !activeParishId.equals(requestedParishId)) {
            throw commonValidationHelper.forbidden("Không thể chuyển leader ra ngoài giáo xứ hiện tại");
        }
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
