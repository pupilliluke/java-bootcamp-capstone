package com.capstone.crm.service;

import com.capstone.crm.api.dto.CreateUserRequest;
import com.capstone.crm.api.dto.UpdateUserRequest;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, passwordEncoder);
    }

    @Test
    void listMapsUsersWithoutExposingPasswordHashes() {
        AppUser agent = user("agent-two", "agent-two@example.test", "stored-hash", UserRole.AGENT);
        AppUser admin = user("admin-two", "admin-two@example.test", "other-hash", UserRole.ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(agent, admin));

        List<UserResponse> responses = service.list();

        assertThat(responses)
                .extracting(UserResponse::username)
                .containsExactly("agent-two", "admin-two");
        assertThat(responses)
                .extracting(UserResponse::role)
                .containsExactly(UserRole.AGENT, UserRole.ADMIN);
    }

    @Test
    void createEncodesPasswordBeforeSaving() {
        CreateUserRequest request = new CreateUserRequest(
                "new-agent", "new-agent@example.test", "password123", UserRole.AGENT);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.create(request);

        ArgumentCaptor<AppUser> savedUser = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getValue().getPasswordHash()).isNotEqualTo(request.password());
        assertThat(response.username()).isEqualTo("new-agent");
        assertThat(response.role()).isEqualTo(UserRole.AGENT);
    }

    @Test
    void getRejectsUnknownUserId() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found: 404");
    }

    @Test
    void updateChangesEditableFieldsAndEncodesNewPassword() {
        AppUser existing = user("agent-two", "old@example.test", "old-hash", UserRole.AGENT);
        UpdateUserRequest request = new UpdateUserRequest(
                "updated@example.test", "new-password", UserRole.ADMIN, false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponse response = service.update(2L, request, "admin1");

        assertThat(existing.getEmail()).isEqualTo("updated@example.test");
        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
        assertThat(response.email()).isEqualTo("updated@example.test");
        verify(passwordEncoder).encode("new-password");
        verify(userRepository).save(existing);
    }

    @Test
    void updateLeavesPasswordAloneWhenNewPasswordIsBlank() {
        AppUser existing = user("agent-two", "old@example.test", "old-hash", UserRole.AGENT);
        UpdateUserRequest request = new UpdateUserRequest(
                "updated@example.test", "", UserRole.AGENT, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        service.update(2L, request, "admin1");

        assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateRejectsSelfModification() {
        AppUser currentAdmin = user("admin1", "admin1@example.test", "hash", UserRole.ADMIN);
        UpdateUserRequest request = new UpdateUserRequest(
                "changed@example.test", null, UserRole.ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentAdmin));

        assertThatThrownBy(() -> service.update(1L, request, "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Administrators cannot update their own account");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteRemovesAnotherUser() {
        AppUser otherUser = user("agent-two", "agent-two@example.test", "hash", UserRole.AGENT);
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        service.delete(2L, "admin1");

        verify(userRepository).delete(otherUser);
    }

    @Test
    void deleteRejectsSelfDeletion() {
        AppUser currentAdmin = user("admin1", "admin1@example.test", "hash", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentAdmin));

        assertThatThrownBy(() -> service.delete(1L, "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Administrators cannot delete their own account");
        verify(userRepository, never()).delete(any());
    }

    private AppUser user(String username, String email, String passwordHash, UserRole role) {
        return new AppUser(username, email, passwordHash, role);
    }
}
