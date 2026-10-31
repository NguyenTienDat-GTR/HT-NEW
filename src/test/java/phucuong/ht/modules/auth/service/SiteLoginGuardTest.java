package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SiteLoginGuardTest {

    private static final UUID PHU_CUONG = UUID.randomUUID();
    private static final UUID SAI_GON = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private DioceseRepository dioceseRepository;

    private SiteLoginGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SiteLoginGuard("example.com", accountRepository, dioceseRepository);
        lenient().when(dioceseRepository.findBySiteSubdomain("phucuong")).thenReturn(Optional.of(diocese(PHU_CUONG)));
        lenient().when(dioceseRepository.findBySiteSubdomain("saigon")).thenReturn(Optional.of(diocese(SAI_GON)));
        lenient().when(dioceseRepository.findBySiteSubdomain("staging")).thenReturn(Optional.empty());
    }

    @Test
    void accountOfOtherDioceseIsRejectedAtThisSubdomain() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("leader1")).thenReturn(Optional.of(PHU_CUONG));

        ApiException exception = assertThrows(ApiException.class, () -> guard.assertCanLogin("leader1", "saigon.example.com"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void accountOfSameDioceseIsAllowed() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("leader1")).thenReturn(Optional.of(PHU_CUONG));

        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", "phucuong.example.com"));
        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", "PhuCuong.Example.com:443"));
    }

    @Test
    void systemWideAccountIsAllowedAtAnySubdomain() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("superadminpc")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.assertCanLogin("superadminpc", "saigon.example.com"));
    }

    @Test
    void mainDomainAndUnmappedSubdomainsAreNotRestricted() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("leader1")).thenReturn(Optional.of(PHU_CUONG));

        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", "example.com"));
        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", "staging.example.com"));
        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", "other-domain.org"));
        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", "a.saigon.example.com"));
    }

    @Test
    void missingHostOrBlankBaseDomainDisablesTheCheck() {
        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", null));
        assertDoesNotThrow(() -> guard.assertCanLogin("leader1", ""));

        SiteLoginGuard disabled = new SiteLoginGuard("", accountRepository, dioceseRepository);
        assertDoesNotThrow(() -> disabled.assertCanLogin("leader1", "saigon.example.com"));
        verifyNoInteractions(accountRepository);
    }

    private static Diocese diocese(UUID id) {
        Diocese diocese = new Diocese();
        diocese.setId(id);
        return diocese;
    }
}
