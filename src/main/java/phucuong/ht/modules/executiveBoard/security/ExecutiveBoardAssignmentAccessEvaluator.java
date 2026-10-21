package phucuong.ht.modules.executiveBoard.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;

import java.util.UUID;

@Component("executiveBoardAssignmentAccessEvaluator")
public class ExecutiveBoardAssignmentAccessEvaluator {

    public static final String CREATE_DIOCESE = "organization.executive_board_assignment.create.diocese";
    public static final String READ_DIOCESE = "organization.executive_board_assignment.read.diocese";
    public static final String UPDATE_DIOCESE = "organization.executive_board_assignment.update.diocese";
    public static final String CREATE_DEANERY = "organization.executive_board_assignment.create.deanery";
    public static final String READ_DEANERY = "organization.executive_board_assignment.read.deanery";
    public static final String UPDATE_DEANERY = "organization.executive_board_assignment.update.deanery";
    public static final String CREATE_PARISH = "organization.executive_board_assignment.create.parish";
    public static final String READ_PARISH = "organization.executive_board_assignment.read.parish";
    public static final String UPDATE_PARISH = "organization.executive_board_assignment.update.parish";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final ExecutiveBoardAssignmentRepository executiveBoardAssignmentRepository;

    public ExecutiveBoardAssignmentAccessEvaluator(
            securityUtils securityUtils,
            AccountRepository accountRepository,
            ExecutiveBoardAssignmentRepository executiveBoardAssignmentRepository
    ) {
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
        this.executiveBoardAssignmentRepository = executiveBoardAssignmentRepository;
    }

    public boolean canCreate(Authentication authentication, UnitType unitType) {
        return hasAuthorityForTarget(authentication, unitType, "create");
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_DIOCESE)
                || hasAuthority(authentication, READ_DEANERY)
                || hasAuthority(authentication, READ_PARISH);
    }

    public boolean canRead(Authentication authentication, UUID id) {
        return hasScopedAccess(authentication, id, "read");
    }

    public boolean canToggle(Authentication authentication, UUID id) {
        return hasScopedAccess(authentication, id, "update");
    }

    private boolean hasScopedAccess(Authentication authentication, UUID id, String action) {
        if (id == null) {
            return false;
        }

        return executiveBoardAssignmentRepository.findByIdWithRelations(id)
                .map(assignment -> hasAuthorityForTarget(authentication, assignment.getUnitType(), action)
                        && matchesScope(authentication, assignment))
                .orElse(false);
    }

    private boolean matchesScope(Authentication authentication, ExecutiveBoardAssignment assignment) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        if (assignment.getDiocese() != null && dioceseId != null && dioceseId.equals(assignment.getDiocese().getId())) {
            return true;
        }

        UUID deaneryId = accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
        if (assignment.getDeanery() != null && deaneryId != null && deaneryId.equals(assignment.getDeanery().getId())) {
            return true;
        }

        UUID parishId = accountRepository.findActiveParishIdByUsername(username).orElse(null);
        if (assignment.getParish() != null) {
            return parishId != null && parishId.equals(assignment.getParish().getId());
        }

        return false;
    }

    private String requiredAuthority(UnitType unitType, String action) {
        if (unitType == null) {
            return null;
        }
        return switch (unitType) {
            case DIOCESE -> "organization.executive_board_assignment." + action + ".diocese";
            case DEANERY -> "organization.executive_board_assignment." + action + ".deanery";
            case PARISH -> "organization.executive_board_assignment." + action + ".parish";
        };
    }

    // Mỗi tài khoản chỉ thao tác trên phân công thuộc đúng cấp mình được cấp quyền (không kế thừa cấp dưới).
    private boolean hasAuthorityForTarget(Authentication authentication, UnitType unitType, String action) {
        return hasAuthority(authentication, requiredAuthority(unitType, action));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authority != null && securityUtils.hasAuthority(authentication, authority);
    }
}
