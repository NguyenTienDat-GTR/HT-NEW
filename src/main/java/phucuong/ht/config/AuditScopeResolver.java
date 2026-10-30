package phucuong.ht.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import org.springframework.security.core.Authentication;

import phucuong.ht.config.audit.AuditScopeSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditScopeResolver {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final Map<String, AuditScopeSource> sourcesByResource = new HashMap<>();

    public AuditScopeResolver(securityUtils securityUtils, AccountRepository accountRepository, List<AuditScopeSource> sources) {
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
        for (AuditScopeSource source : sources) {
            sourcesByResource.put(source.resource(), source);
        }
    }

    public AuditScopeContext resolve(Authentication authentication, String requestPath, String resource, String resourceId) {
        AuditScopeContext actorScope = resolveFromAuthentication(authentication);
        AuditScopeContext resourceScope = resolveFromResource(resource, resourceId);
        return AuditScopeContext.builder()
                .unitType(resourceScope.getUnitType() != null ? resourceScope.getUnitType() : actorScope.getUnitType())
                .unitId(resourceScope.getUnitId() != null ? resourceScope.getUnitId() : actorScope.getUnitId())
                .dioceseId(resourceScope.getDioceseId() != null ? resourceScope.getDioceseId() : actorScope.getDioceseId())
                .deaneryId(resourceScope.getDeaneryId() != null ? resourceScope.getDeaneryId() : actorScope.getDeaneryId())
                .parishId(resourceScope.getParishId() != null ? resourceScope.getParishId() : actorScope.getParishId())
                .build();
    }

    public AuditScopeContext resolveFromAuthentication(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            return empty();
        }

        UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        UUID deaneryId = accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
        UUID parishId = accountRepository.findActiveParishIdByUsername(username).orElse(null);

        UnitType unitType = null;
        UUID unitId = null;
        if (parishId != null) {
            unitType = UnitType.PARISH;
            unitId = parishId;
        } else if (deaneryId != null) {
            unitType = UnitType.DEANERY;
            unitId = deaneryId;
        } else if (dioceseId != null) {
            unitType = UnitType.DIOCESE;
            unitId = dioceseId;
        }

        return AuditScopeContext.builder()
                .unitType(unitType)
                .unitId(unitId)
                .dioceseId(dioceseId)
                .deaneryId(deaneryId)
                .parishId(parishId)
                .build();
    }

    public AuditScopeContext resolveFromResource(String resource, String resourceId) {
        if (!StringUtils.hasText(resource) || !StringUtils.hasText(resourceId)) {
            return empty();
        }
        AuditScopeSource source = sourcesByResource.get(resource);
        if (source == null) {
            return empty();
        }
        try {
            return source.resolve(resourceId);
        } catch (IllegalArgumentException exception) {
            return empty();
        }
    }

    private AuditScopeContext empty() {
        return AuditScopeContext.empty();
    }
}
