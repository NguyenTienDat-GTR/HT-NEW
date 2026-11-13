package phucuong.ht.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Cấp của tài khoản lấy từ hậu tố quyền; không phụ thuộc việc tài khoản gắn trực tiếp đơn vị nào. */
class AccountScopeResolverLevelTest {

    private static Authentication auth(String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("u", null, List.of(), List.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void highestLevelWins() {
        assertEquals(AccountScopeResolver.Level.DIOCESE, AccountScopeResolver.levelOf(auth("x.read.parish", "y.update.diocese", "z.read.deanery")));
        assertEquals(AccountScopeResolver.Level.DEANERY, AccountScopeResolver.levelOf(auth("x.read.parish", "z.read.deanery")));
        assertEquals(AccountScopeResolver.Level.PARISH, AccountScopeResolver.levelOf(auth("x.read.parish")));
    }

    @Test
    void noScopedPermissionMeansNoLevel() {
        assertNull(AccountScopeResolver.levelOf(auth("system.something")));
        assertNull(AccountScopeResolver.levelOf(null));
    }
}
