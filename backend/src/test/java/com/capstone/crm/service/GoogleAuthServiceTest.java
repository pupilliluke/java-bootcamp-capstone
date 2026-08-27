package com.capstone.crm.service;

import com.capstone.crm.api.dto.LoginResponseDTO;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.exception.AccountPendingApprovalException;
import com.capstone.crm.exception.InvalidCredentialsException;
import com.capstone.crm.repository.AppUserRepository;
import com.capstone.crm.security.GoogleIdentity;
import com.capstone.crm.security.GoogleTokenVerifier;
import com.capstone.crm.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock GoogleTokenVerifier verifier;
    @Mock AppUserRepository users;
    @Mock JwtService jwtService;

    @InjectMocks GoogleAuthService service;

    private static GoogleIdentity identity(String email, boolean verified) {
        return new GoogleIdentity("google-sub-123", email, verified, "Test Person");
    }

    @Test
    void issuesATokenForAKnownEnabledAccount() {
        when(verifier.verify("id-token")).thenReturn(identity("agent@example.test", true));
        AppUser existing = new AppUser("agent1", "agent@example.test", "hash", UserRole.AGENT);
        when(users.findByEmailIgnoreCase("agent@example.test")).thenReturn(Optional.of(existing));
        when(jwtService.issueToken("agent1", "AGENT")).thenReturn("signed-jwt");

        LoginResponseDTO response = service.authenticate("id-token");

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.username()).isEqualTo("agent1");
        assertThat(response.role()).isEqualTo("AGENT");
        verify(users, never()).save(any());
    }

    @Test
    void matchesTheAccountByEmailCaseInsensitivelyAndIgnoresGooglesRoleView() {
        // The email match is what resolves the account; the ID token has no say
        // over the role, which comes from the stored AppUser.
        when(verifier.verify(anyString())).thenReturn(identity("Admin@Example.Test", true));
        AppUser admin = new AppUser("admin1", "admin@example.test", "hash", UserRole.ADMIN);
        when(users.findByEmailIgnoreCase("Admin@Example.Test")).thenReturn(Optional.of(admin));
        when(jwtService.issueToken("admin1", "ADMIN")).thenReturn("admin-jwt");

        LoginResponseDTO response = service.authenticate("id-token");

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.accessToken()).isEqualTo("admin-jwt");
    }

    @Test
    void provisionsADisabledAgentOnFirstSignInThenWithholdsAccess() {
        when(verifier.verify(anyString())).thenReturn(identity("newcomer@example.test", true));
        when(users.findByEmailIgnoreCase("newcomer@example.test")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.authenticate("id-token"))
                .isInstanceOf(AccountPendingApprovalException.class);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        AppUser created = saved.getValue();
        assertThat(created.getRole()).isEqualTo(UserRole.AGENT);
        assertThat(created.isEnabled()).isFalse();
        assertThat(created.getEmail()).isEqualTo("newcomer@example.test");
        assertThat(created.getUsername()).isEqualTo("newcomer@example.test");
        // Google accounts carry no local password.
        assertThat(created.getPasswordHash()).isNull();
        // A provisioned-but-unapproved account never gets a token.
        verify(jwtService, never()).issueToken(anyString(), anyString());
    }

    @Test
    void refusesAKnownButDisabledAccountWithoutCreatingAnother() {
        when(verifier.verify(anyString())).thenReturn(identity("waiting@example.test", true));
        AppUser disabled = new AppUser("waiting", "waiting@example.test", null, UserRole.AGENT);
        disabled.setEnabled(false);
        when(users.findByEmailIgnoreCase("waiting@example.test")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.authenticate("id-token"))
                .isInstanceOf(AccountPendingApprovalException.class);

        verify(users, never()).save(any());
        verify(jwtService, never()).issueToken(anyString(), anyString());
    }

    @Test
    void rejectsAnUnverifiedEmailAndProvisionsNothing() {
        when(verifier.verify(anyString())).thenReturn(identity("unverified@example.test", false));

        assertThatThrownBy(() -> service.authenticate("id-token"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(users, never()).findByEmailIgnoreCase(anyString());
        verify(users, never()).save(any());
    }

    @Test
    void propagatesAnInvalidTokenFromTheVerifier() {
        when(verifier.verify(anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid Google token"));

        assertThatThrownBy(() -> service.authenticate("forged"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(users, never()).save(any());
    }
}
