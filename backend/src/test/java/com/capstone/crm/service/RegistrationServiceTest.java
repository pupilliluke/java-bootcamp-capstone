package com.capstone.crm.service;

import com.capstone.crm.api.dto.RegisterRequest;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.exception.DuplicateUserException;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock AppUserRepository users;
    @Mock PasswordEncoder passwordEncoder;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, passwordEncoder);
    }

    private static RegisterRequest request() {
        return new RegisterRequest("new-agent", "new-agent@example.test", "correct-horse-battery");
    }

    @Test
    void createsADisabledAgentWithAHashedPassword() {
        when(users.existsByUsernameIgnoreCase("new-agent")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("new-agent@example.test")).thenReturn(false);
        when(passwordEncoder.encode("correct-horse-battery")).thenReturn("hashed-value");
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = service.register(request());

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());

        // The three things a self-registered account must be, regardless of what
        // the request asked for: an AGENT, disabled, and never storing the raw
        // password.
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.AGENT);
        assertThat(saved.getValue().isEnabled()).isFalse();
        assertThat(saved.getValue().getPasswordHash())
                .isEqualTo("hashed-value")
                .isNotEqualTo("correct-horse-battery");

        assertThat(response.username()).isEqualTo("new-agent");
        assertThat(response.enabled()).isFalse();
        assertThat(response.role()).isEqualTo(UserRole.AGENT);
    }

    @Test
    void rejectsADuplicateUsername() {
        when(users.existsByUsernameIgnoreCase("new-agent")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("new-agent");

        verify(users, never()).save(any());
    }

    @Test
    void rejectsADuplicateEmail() {
        when(users.existsByUsernameIgnoreCase("new-agent")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("new-agent@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("new-agent@example.test");

        verify(users, never()).save(any());
    }

    @Test
    void trimsSurroundingWhitespaceBeforeStoring() {
        when(users.existsByUsernameIgnoreCase("spaced-user")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("spaced@example.test")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        service.register(new RegisterRequest(
                "  spaced-user  ", "  spaced@example.test  ", "correct-horse-battery"));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("spaced-user");
        assertThat(saved.getValue().getEmail()).isEqualTo("spaced@example.test");
    }
}
