package phucuong.ht.modules.system.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.repository.SystemLogRepository;

@Component("systemLogAccessEvaluator")
public class SystemLogAccessEvaluator {

    public static final String READ_ALL = "system.audit_log.read.all";
    public static final String READ_DIOCESE = "system.audit_log.read.diocese";
    public static final String READ_DEANERY = "system.audit_log.read.deanery";
    public static final String READ_PARISH = "system.audit_log.read.parish";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final SystemLogRepository systemLogRepository;

    public SystemLogAccessEvaluator(securityUtils securityUtils, AccountRepository accountRepository, SystemLogRepository systemLogRepository) {
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
        this.systemLogRepository = systemLogRepository;
    }

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_ALL)
                || securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, READ_DEANERY)
                || securityUtils.hasAuthority(authentication, READ_PARISH);
    }

    public boolean canRead(Authentication authentication, String id) {
        if (securityUtils.hasAuthority(authentication, READ_ALL)) {
            return true;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        if (securityUtils.hasAuthority(authentication, READ_DIOCESE)) {
            return accountRepository.findActiveDioceseIdByUsername(username)
                    .flatMap(activeDioceseId -> systemLogRepository.findDioceseIdByLogId(id).map(activeDioceseId::equals))
                    .orElse(false);
        }

        if (securityUtils.hasAuthority(authentication, READ_DEANERY)) {
            return accountRepository.findActiveDeaneryIdByUsername(username)
                    .flatMap(activeDeaneryId -> systemLogRepository.findDeaneryIdByLogId(id).map(activeDeaneryId::equals))
                    .orElse(false);
        }

        if (securityUtils.hasAuthority(authentication, READ_PARISH)) {
            return accountRepository.findActiveParishIdByUsername(username)
                    .flatMap(activeParishId -> systemLogRepository.findParishIdByLogId(id).map(activeParishId::equals))
                    .orElse(false);
        }

        return false;
    }
}
