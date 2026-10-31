package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SiteHostPolicyTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private DioceseRepository dioceseRepository;

    private SiteHostPolicy policy;

    @BeforeEach
    void setUp() {
        SiteLoginGuard guard = new SiteLoginGuard("example.com", accountRepository, dioceseRepository);
        policy = new SiteHostPolicy(guard, dioceseRepository);
        lenient().when(dioceseRepository.findBySiteSubdomain("phucuong")).thenReturn(Optional.of(diocese(true)));
        lenient().when(dioceseRepository.findBySiteSubdomain("daydung")).thenReturn(Optional.of(diocese(false)));
        lenient().when(dioceseRepository.findBySiteSubdomain("test")).thenReturn(Optional.of(diocese(true)));
        lenient().when(dioceseRepository.findBySiteSubdomain("khongco")).thenReturn(Optional.empty());
    }

    @Test
    void activeDioceseSubdomainIsServed() {
        assertTrue(policy.isServableDioceseHost("phucuong.example.com"));
        assertTrue(policy.isServableDioceseHost("PhuCuong.Example.com"));
    }

    @Test
    void inactiveOrUnknownDioceseIsNotServed() {
        assertFalse(policy.isServableDioceseHost("daydung.example.com"));
        assertFalse(policy.isServableDioceseHost("khongco.example.com"));
    }

    @Test
    void reservedLabelsAreNeverServedEvenIfADioceseUsesThem() {
        assertFalse(policy.isServableDioceseHost("test.example.com"));
        assertFalse(policy.isServableDioceseHost("www.example.com"));
    }

    @Test
    void mainDomainNestedAndForeignDomainsAreNotServed() {
        assertFalse(policy.isServableDioceseHost("example.com"));
        assertFalse(policy.isServableDioceseHost("a.phucuong.example.com"));
        assertFalse(policy.isServableDioceseHost("phucuong.attacker.com"));
        assertFalse(policy.isServableDioceseHost(""));
        assertFalse(policy.isServableDioceseHost(null));
    }

    @Test
    void blankBaseDomainServesNothing() {
        SiteHostPolicy disabled = new SiteHostPolicy(new SiteLoginGuard("", accountRepository, dioceseRepository), dioceseRepository);

        assertFalse(disabled.isServableDioceseHost("phucuong.example.com"));
        verifyNoInteractions(accountRepository);
    }

    private static Diocese diocese(boolean active) {
        Diocese diocese = new Diocese();
        diocese.setStatus(active);
        return diocese;
    }
}
