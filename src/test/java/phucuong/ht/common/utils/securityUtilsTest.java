package phucuong.ht.common.utils;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class securityUtilsTest {

    private final securityUtils securityUtils = new securityUtils();

    @Test
    void currentAuditActorShouldUseUsernameWhenPrincipalHasNoLeader() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "phucuongadmin",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of("system.role.create.all")
        );

        assertEquals("phucuongadmin", securityUtils.currentAuditActor(authentication(principal)));
    }

    @Test
    void currentAuditActorShouldPreferLeaderIdWhenPresent() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-parish",
                "leader-1",
                List.of("ADMIN_PARISH"),
                List.of("system.role.create.all")
        );

        assertEquals("leader-1", securityUtils.currentAuditActor(authentication(principal)));
    }

    @Test
    void currentAuditActorShouldKeepSuperAdminSystemActor() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadmin",
                null,
                List.of("SUPER_ADMIN"),
                List.of("system.role.create.all")
        );

        assertEquals("super-admin", securityUtils.currentAuditActor(authentication(principal)));
    }

    private UsernamePasswordAuthenticationToken authentication(AuthUserDetails principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
