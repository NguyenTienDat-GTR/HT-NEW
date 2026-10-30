package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.EmailUpdateRequest;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyEmailServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordOtpStore otpStore;
    @InjectMocks MyEmailService service;

    private Authentication auth() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("phucuongadmin", null, List.of("ADMIN_DIOCESE"), List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private EmailUpdateRequest request(String email, String password) {
        EmailUpdateRequest request = new EmailUpdateRequest();
        request.setEmail(email);
        request.setCurrentPassword(password);
        return request;
    }

    private Account account() {
        Account account = new Account();
        account.setUsername("phucuongadmin");
        account.setPassword("hash");
        account.setEmail("old@phucuong.vn");
        return account;
    }

    @Test
    void updatesEmailWhenCurrentPasswordMatchesAndNormalizesCase() {
        Account account = account();
        when(accountRepository.findActiveByUsername("phucuongadmin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        var response = service.update(request(" New@PhuCuong.vn ", "secret"), auth());

        assertEquals("new@phucuong.vn", response.getEmail());
        assertEquals("new@phucuong.vn", account.getEmail());
        verify(accountRepository).saveAndFlush(account);
        verify(otpStore).clearOldPasswordFailures("phucuongadmin");
    }

    @Test
    void rejectsWrongPasswordAndKeepsOldEmail() {
        Account account = account();
        when(accountRepository.findActiveByUsername("phucuongadmin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(ApiException.class, () -> service.update(request("new@phucuong.vn", "wrong"), auth()));

        assertEquals("old@phucuong.vn", account.getEmail());
        verify(otpStore).recordOldPasswordFailure(anyString(), any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAccountLinkedToLeaderBecauseItUsesLeaderEmail() {
        Account account = account();
        account.setLeader(new Leader());
        when(accountRepository.findActiveByUsername("phucuongadmin")).thenReturn(Optional.of(account));

        assertThrows(ApiException.class, () -> service.update(request("new@phucuong.vn", "secret"), auth()));

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void locksOutAfterTooManyPasswordFailures() {
        when(otpStore.oldPasswordFailures("phucuongadmin")).thenReturn(5L);

        assertThrows(ApiException.class, () -> service.update(request("new@phucuong.vn", "secret"), auth()));

        verify(accountRepository, never()).findActiveByUsername(anyString());
    }
}
