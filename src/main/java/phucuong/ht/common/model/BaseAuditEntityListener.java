package phucuong.ht.common.model;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.time.OffsetDateTime;

public class BaseAuditEntityListener {

    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String SUPER_ADMIN_ACTOR = "super-admin";
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final int AUDIT_ACTOR_MAX_LENGTH = 100;

    @PrePersist
    public void beforeCreate(BaseModel entity) {
        OffsetDateTime now = OffsetDateTime.now();
        String actor = currentAuditActor();

        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(entity.getCreatedAt());
        }
        if (!StringUtils.hasText(entity.getCreatedBy())) {
            entity.setCreatedBy(actor);
        }
        if (!StringUtils.hasText(entity.getUpdatedBy())) {
            entity.setUpdatedBy(entity.getCreatedBy());
        }
        if (entity.getStatus() == null) {
            entity.setStatus(Boolean.TRUE);
        }
    }

    @PreUpdate
    public void beforeUpdate(BaseModel entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        if (!StringUtils.hasText(entity.getUpdatedBy())) {
            entity.setUpdatedBy(currentAuditActor());
        }
    }

    private String currentAuditActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        if (hasAuthority(authentication, ROLE_SUPER_ADMIN)) {
            return SUPER_ADMIN_ACTOR;
        }
        if (authentication.getPrincipal() instanceof AuthUserDetails principal) {
            if (StringUtils.hasText(principal.getLeaderId())) {
                return truncate(principal.getLeaderId());
            }
            if (StringUtils.hasText(principal.getUsername())) {
                return truncate(principal.getUsername());
            }
        }

        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equals(name)) {
            return SYSTEM_ACTOR;
        }
        return truncate(name);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private String truncate(String actor) {
        if (actor.length() <= AUDIT_ACTOR_MAX_LENGTH) {
            return actor;
        }
        return actor.substring(0, AUDIT_ACTOR_MAX_LENGTH);
    }
}
