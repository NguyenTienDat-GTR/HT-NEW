package phucuong.ht.modules.position.security;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.security.AuthorityChecks;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.UUID;

/**
 * Chức vụ thuộc riêng từng đơn vị: giáo phận, giáo hạt và giáo xứ tự tạo chức vụ cho cấp của mình.
 * Chỉ đơn vị sở hữu được tạo, sửa và đổi trạng thái chức vụ; cấp trên trực tiếp (giáo phận, giáo hạt) chỉ được xem chức vụ của đơn vị bên dưới.
 */
public final class PositionOwnership {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;

    public PositionOwnership(AccountRepository accountRepository, PositionRepository positionRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * Phạm vi đơn vị của một tài khoản; các trường là null nếu tài khoản không thuộc đơn vị đó. Đơn vị luôn suy ra từ phạm vi hiệu lực
     * (gắn trực tiếp hoặc qua giáo xứ / huynh trưởng). dioceseAdmin / deaneryAdmin là đơn vị của tài khoản ở đúng cấp quản lý của nó
     * (cấp lấy từ quyền chức vụ), null nếu tài khoản không quản lý ở cấp đó.
     */
    public record Viewer(UUID diocese, UUID dioceseAdmin, UUID ownDeanery, UUID deaneryAdmin, UUID ownParish) {
    }

    public Viewer viewer(String username, Authentication authentication) {
        if (username == null || username.isBlank()) {
            return new Viewer(null, null, null, null, null);
        }
        UUID diocese = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        UUID deanery = accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
        boolean dioceseLevel = hasAnyPositionAuthority(authentication, "diocese");
        boolean deaneryLevel = !dioceseLevel && hasAnyPositionAuthority(authentication, "deanery");
        return new Viewer(diocese, dioceseLevel ? diocese : null, deanery, deaneryLevel ? deanery : null,
                accountRepository.findActiveParishIdByUsername(username).orElse(null));
    }

    private static boolean hasAnyPositionAuthority(Authentication authentication, String scope) {
        for (String action : new String[]{"create", "read", "update", "toggle"}) {
            if (AuthorityChecks.hasAuthority(authentication, "organization.position." + action + "." + scope)) {
                return true;
            }
        }
        return false;
    }

    /** Đơn vị mà tài khoản được tạo chức vụ cho ở cấp này; null nếu tài khoản không có đơn vị ở cấp đó. */
    public UUID ownerUnitForCreate(Viewer viewer, UnitType type) {
        return switch (type) {
            case DIOCESE -> viewer.diocese();
            case DEANERY -> viewer.deaneryAdmin();
            case PARISH -> viewer.ownParish();
        };
    }

    /** Chỉ đơn vị sở hữu mới được sửa hoặc đổi trạng thái chức vụ; cấp trên chỉ được xem. */
    public boolean canManage(Viewer viewer, Position position) {
        if (position.getPositionType() == null || position.getUnitId() == null) {
            return false;
        }
        return position.getUnitId().equals(ownerUnitForCreate(viewer, position.getPositionType()));
    }

    public boolean canAccess(Viewer viewer, Position position) {
        if (viewer.diocese() == null || position.getPositionType() == null) {
            return false;
        }
        boolean dioceseAdmin = viewer.dioceseAdmin() != null && viewer.dioceseAdmin().equals(position.getDioceseId());
        return switch (position.getPositionType()) {
            case DIOCESE -> viewer.diocese().equals(position.getDioceseId());
            case DEANERY -> dioceseAdmin || position.getUnitId().equals(viewer.ownDeanery());
            case PARISH -> dioceseAdmin
                    || position.getUnitId().equals(viewer.ownParish())
                    || (viewer.deaneryAdmin() != null
                    && positionRepository.deaneryIdOfParish(position.getUnitId()).filter(viewer.deaneryAdmin()::equals).isPresent());
        };
    }
}
