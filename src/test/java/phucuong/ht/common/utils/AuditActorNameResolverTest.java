package phucuong.ht.common.utils;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditActorNameResolverTest {

    private final LeaderRepository leaderRepository = mock(LeaderRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AuditActorNameResolver resolver = new AuditActorNameResolver(leaderRepository, accountRepository);

    @Test
    void resolvesSpecialActorsWithoutQueries() {
        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve(" "));
        assertEquals("Quản trị viên", resolver.resolve("super-admin"));
        assertEquals("Hệ thống", resolver.resolve("SYSTEM"));
        assertEquals("Hệ thống tự duyệt", resolver.resolve("SYSTEM_AUTO_APPROVAL"));
        verify(leaderRepository, never()).findById(any());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void resolvesLeaderIdAndCachesResult() {
        UUID leaderId = UUID.randomUUID();
        Leader leader = new Leader();
        leader.setHolyName("Giuse");
        leader.setLastName("Nguyễn Văn");
        leader.setFirstName("An");
        when(leaderRepository.findById(leaderId)).thenReturn(Optional.of(leader));

        assertEquals("Giuse Nguyễn Văn An", resolver.resolve(leaderId.toString()));
        assertEquals("Giuse Nguyễn Văn An", resolver.resolve(leaderId.toString()));
        verify(leaderRepository, times(1)).findById(leaderId);
    }

    @Test
    void resolvesUsernameThroughLinkedLeaderOrFallsBackToRawActor() {
        Leader leader = new Leader();
        leader.setLastName("Trần");
        leader.setFirstName("Bình");
        Account linked = new Account();
        linked.setUsername("binh");
        linked.setLeader(leader);
        Account unlinked = new Account();
        unlinked.setUsername("phucuongadmin");
        when(accountRepository.findById("binh")).thenReturn(Optional.of(linked));
        when(accountRepository.findById("phucuongadmin")).thenReturn(Optional.of(unlinked));
        when(accountRepository.findById("ghost")).thenReturn(Optional.empty());

        assertEquals("Trần Bình", resolver.resolve("binh"));
        assertEquals("phucuongadmin", resolver.resolve("phucuongadmin"));
        assertEquals("ghost", resolver.resolve("ghost"));
    }
}
