package phucuong.ht.modules.training.scoreFormulaTemplate.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.scoreFormulaTemplate.repository.ScoreFormulaTemplateRepository;

@Component("scoreFormulaTemplateAccessEvaluator")
@RequiredArgsConstructor
public class ScoreFormulaTemplateAccessEvaluator {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final ScoreFormulaTemplateRepository repository;

    public boolean canCreate(Authentication authentication, UnitType unitType) {
        if (unitType == null) {
            return false;
        }
        return switch (unitType) {
            case DIOCESE -> securityUtils.hasAuthority(authentication, "training.score_formula.create.diocese");
            case DEANERY -> securityUtils.hasAuthority(authentication, "training.score_formula.create.deanery");
            default -> false;
        };
    }

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "training.score_formula.read.diocese")
                || securityUtils.hasAuthority(authentication, "training.score_formula.read.deanery");
    }

    public boolean canRead(Authentication authentication, String id) {
        return hasScopedAccess(authentication, id, "read");
    }

    public boolean canUpdate(Authentication authentication, String id) {
        return hasScopedAccess(authentication, id, "update");
    }

    public boolean canToggle(Authentication authentication, String id) {
        return hasScopedAccess(authentication, id, "toggle");
    }

    private boolean hasScopedAccess(Authentication authentication, String id, String action) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        if (securityUtils.hasAuthority(authentication, "training.score_formula." + action + ".diocese")) {
            return accountRepository.findActiveDioceseIdByUsername(username)
                    .flatMap(activeId -> repository.findDioceseIdByFormulaId(id).map(activeId::equals))
                    .orElse(false);
        }
        if (securityUtils.hasAuthority(authentication, "training.score_formula." + action + ".deanery")) {
            return accountRepository.findActiveDeaneryIdByUsername(username)
                    .flatMap(activeId -> repository.findDeaneryIdByFormulaId(id).map(activeId::equals))
                    .orElse(false);
        }
        return false;
    }
}
