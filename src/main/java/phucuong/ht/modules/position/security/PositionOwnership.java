package phucuong.ht.modules.position.security;

import phucuong.ht.common.enums.UnitType;
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

    /** Phạm vi đơn vị của một tài khoản; các trường là null nếu tài khoản không thuộc đơn vị đó. */
    public record Viewer(UUID diocese, UUID directDiocese, UUID ownDeanery, UUID directDeanery, UUID ownParish) {
    }

    public Viewer viewer(String username) {
        if (username == null || username.isBlank()) {
            return new Viewer(null, null, null, null, null);
        }
        return new Viewer(
                accountRepository.findActiveDioceseIdByUsername(username).orElse(null),
                accountRepository.findDirectActiveDioceseIdByUsername(username).orElse(null),
                accountRepository.findActiveDeaneryIdByUsername(username).orElse(null),
                accountRepository.findDirectActiveDeaneryIdByUsername(username).orElse(null),
                accountRepository.findActiveParishIdByUsername(username).orElse(null));
    }

    /** Đơn vị mà tài khoản được tạo chức vụ cho ở cấp này; null nếu tài khoản không có đơn vị ở cấp đó. */
    public UUID ownerUnitForCreate(Viewer viewer, UnitType type) {
        return switch (type) {
            case DIOCESE -> viewer.diocese();
            case DEANERY -> viewer.directDeanery();
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
        boolean dioceseAdmin = viewer.directDiocese() != null && viewer.directDiocese().equals(position.getDioceseId());
        return switch (position.getPositionType()) {
            case DIOCESE -> viewer.diocese().equals(position.getDioceseId());
            case DEANERY -> dioceseAdmin || position.getUnitId().equals(viewer.ownDeanery());
            case PARISH -> dioceseAdmin
                    || position.getUnitId().equals(viewer.ownParish())
                    || (viewer.directDeanery() != null
                    && positionRepository.deaneryIdOfParish(position.getUnitId()).filter(viewer.directDeanery()::equals).isPresent());
        };
    }
}
