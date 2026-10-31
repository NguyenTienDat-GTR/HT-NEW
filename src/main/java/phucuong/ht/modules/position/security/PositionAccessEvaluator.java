package phucuong.ht.modules.position.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

@Component("positionAccessEvaluator")
@RequiredArgsConstructor
public class PositionAccessEvaluator {

    private final securityUtils securityUtils;
    private final PositionRepository positionRepository;
    private final AccountRepository accountRepository;

    private PositionOwnership ownership() {
        return new PositionOwnership(accountRepository, positionRepository);
    }

    public boolean canCreate(Authentication authentication, UnitType positionType) {
        if (!hasAuthorityForType(authentication, positionType, "create")) {
            return false;
        }
        PositionOwnership ownership = ownership();
        return ownership.ownerUnitForCreate(ownership.viewer(securityUtils.currentUsername(authentication)), positionType) != null;
    }

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "organization.position.read.diocese")
                || securityUtils.hasAuthority(authentication, "organization.position.read.deanery")
                || securityUtils.hasAuthority(authentication, "organization.position.read.parish");
    }

    public boolean canRead(Authentication authentication, String positionCode) {
        return positionRepository.findById(positionCode)
                .map(position -> hasAuthorityForType(authentication, position.getPositionType(), "read") && ownsPosition(authentication, position))
                .orElse(false);
    }

    /**
     * Cần quyền cập nhật ở cấp hiện tại của chức vụ và, nếu đổi cấp, cả ở cấp mới
     * để tài khoản không thể chuyển chức vụ ra ngoài phạm vi mình quản lý.
     */
    public boolean canUpdate(Authentication authentication, String positionCode, UnitType targetType) {
        return positionRepository.findById(positionCode)
                .map(position -> hasAuthorityForType(authentication, position.getPositionType(), "update")
                        && ownsPosition(authentication, position)
                        && (targetType == null || hasAuthorityForType(authentication, targetType, "update")))
                .orElse(false);
    }

    public boolean canToggle(Authentication authentication, String positionCode) {
        return positionRepository.findById(positionCode)
                .map(position -> hasAuthorityForType(authentication, position.getPositionType(), "toggle") && ownsPosition(authentication, position))
                .orElse(false);
    }

    /** Chức vụ thuộc riêng từng đơn vị: chỉ tài khoản của đơn vị đó (hoặc cấp trên trực tiếp) được xem và sửa. */
    private boolean ownsPosition(Authentication authentication, Position position) {
        PositionOwnership ownership = ownership();
        return ownership.canAccess(ownership.viewer(securityUtils.currentUsername(authentication)), position);
    }

    private boolean hasAuthorityForType(Authentication authentication, UnitType unitType, String action) {
        if (unitType == null) {
            return false;
        }

        return switch (unitType) {
            case DIOCESE -> securityUtils.hasAuthority(authentication, "organization.position." + action + ".diocese");
            case DEANERY -> securityUtils.hasAuthority(authentication, "organization.position." + action + ".deanery");
            case PARISH -> securityUtils.hasAuthority(authentication, "organization.position." + action + ".parish");
        };
    }
}
