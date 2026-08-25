package com.capstone.crm.service;

import com.capstone.crm.api.dto.RegisterRequest;
import com.capstone.crm.api.dto.RegistrationResponse;
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

        RegistrationResponse response = service.register(request());

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

        // The response tells the caller their account exists and is waiting, and
        // nothing else. It deliberately carries no id: id is an identity column,
        // so two registrations and a subtraction would reveal how many accounts
        // were created in between.
        assertThat(response.username()).isEqualTo("new-agent");
        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
    }

    // Both collisions produce the identical message. Naming the field would let
    // an anonymous caller check whether a particular person has an account here,
    // and the front end renders this message verbatim.
    @Test
    void rejectsADuplicateUsernameWithoutSayingWhichFieldCollided() {
        when(users.existsByUsernameIgnoreCase("new-agent")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessage("Username or email is already in use");

        verify(users, never()).save(any());
    }

    @Test
    void rejectsADuplicateEmailWithTheSameMessage() {
        when(users.existsByUsernameIgnoreCase("new-agent")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("new-agent@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessage("Username or email is already in use");

        verify(users, never()).save(any());
    }

    // Short-circuiting the second check would make a taken username measurably
    // cheaper to probe than a taken email, which is a difference an anonymous
    // caller can time from outside.
    @Test
    void checksBothFieldsEvenWhenTheUsernameAlreadyFailed() {
        when(users.existsByUsernameIgnoreCase("new-agent")).thenReturn(true);
        when(users.existsByEmailIgnoreCase("new-agent@example.test")).thenReturn(false);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(DuplicateUserException.class);

        verify(users).existsByUsernameIgnoreCase("new-agent");
        verify(users).existsByEmailIgnoreCase("new-agent@example.test");
    }

    // DemoUserSeeder skips a username that already exists, silently. Letting an
    // anonymous caller take "admin1" would leave an environment whose only
    // administrator account belongs to a stranger.
    @Test
    void refusesUsernamesTheDemoSeederDependsOn() {
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("admin1", "not-the-real-admin@example.test", "correct-horse-battery")))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessage("Username or email is already in use");

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
