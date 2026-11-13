package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Quyền sở hữu theo người tạo: đơn vị suy ra từ huynh trưởng / giáo xứ, cấp lấy từ quyền, không từ cột gắn trực tiếp của tài khoản. */
class ManagementOwnershipTest {

    private static final UUID DEANERY_A = UUID.randomUUID();
    private static final UUID DEANERY_B = UUID.randomUUID();

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final Authentication deaneryAdmin = auth("khang", "system.account.manage.deanery");

    private static Authentication auth(String username, String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of(), List.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void account(String username, UUID deanery, int tier) {
        when(accounts.existsByUsername(username)).thenReturn(true);
        when(accounts.findActiveDeaneryIdByUsername(username)).thenReturn(Optional.ofNullable(deanery));
        when(accounts.findHighestActiveRoleOrder(username)).thenReturn(tier);
    }

    @Test
    void peerOfTheSameLevelInTheSameDeaneryOwnsEvenWhenBothAreLinkedThroughLeaders() {
        account("khang", DEANERY_A, 200);
        account("peer", DEANERY_A, 200);
        assertTrue(ManagementOwnership.isOwnedByActorScope(accounts, deaneryAdmin, "khang", "khang", "peer"));
    }

    @Test
    void otherDeaneryOrOtherLevelDoesNotOwn() {
        account("khang", DEANERY_A, 200);
        account("other-deanery", DEANERY_B, 200);
        account("parish-admin", DEANERY_A, 300);
        assertFalse(ManagementOwnership.isOwnedByActorScope(accounts, deaneryAdmin, "khang", "khang", "other-deanery"));
        assertFalse(ManagementOwnership.isOwnedByActorScope(accounts, deaneryAdmin, "khang", "khang", "parish-admin"));
    }

    @Test
    void creatorIsAlwaysOwnerAndAccountsWithoutLevelOrUnitOwnNothingElse() {
        account("khang", DEANERY_A, 200);
        account("peer", DEANERY_A, 200);
        assertTrue(ManagementOwnership.isOwnedByActorScope(accounts, deaneryAdmin, "khang", "khang", "KHANG"));
        assertFalse(ManagementOwnership.isOwnedByActorScope(accounts, auth("khang"), "khang", "khang", "peer"));
        account("lonely", null, 200);
        assertFalse(ManagementOwnership.isOwnedByActorScope(accounts, auth("lonely", "system.account.manage.deanery"), "lonely", "lonely", "peer"));
    }
}
