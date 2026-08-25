package com.capstone.crm.service;

import com.capstone.crm.api.dto.CreateUserRequest;
import com.capstone.crm.api.dto.UpdateUserRequest;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.exception.DuplicateUserException;
import com.capstone.crm.exception.LastAdminException;
import com.capstone.crm.exception.SelfUserModificationException;
import com.capstone.crm.exception.UserNotFoundException;
import com.capstone.crm.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        AppUser user = findUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();
        rejectDuplicateUsername(username);
        rejectDuplicateEmail(email);

        AppUser user = new AppUser(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.role()
        );

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(
            Long userId,
            UpdateUserRequest request,
            String actingUsername) {
        AppUser user = findUser(userId);

        if (user.getUsername().equalsIgnoreCase(actingUsername)) {
            throw new SelfUserModificationException(
                    "Administrators cannot update their own account");
        }

        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new DuplicateUserException("Email is already in use: " + email);
        }
        ensureNotRemovingLastEnabledAdmin(user, request.role(), request.enabled());

        user.setEmail(email);
        user.setRole(request.role());
        user.setEnabled(request.enabled());

        if (request.newPassword() != null
                && !request.newPassword().isBlank()) {
            user.setPasswordHash(
                    passwordEncoder.encode(request.newPassword()));
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(Long userId, String actingUsername) {
        AppUser user = findUser(userId);

        if (user.getUsername().equalsIgnoreCase(actingUsername)) {
            throw new SelfUserModificationException(
                    "Administrators cannot delete their own account");
        }
        ensureNotDeletingLastEnabledAdmin(user);

        userRepository.delete(user);
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found: " + userId));
    }

    private void rejectDuplicateUsername(String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUserException("Username is already in use: " + username);
        }
    }

    private void rejectDuplicateEmail(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException("Email is already in use: " + email);
        }
    }

    private void ensureNotRemovingLastEnabledAdmin(
            AppUser current,
            UserRole requestedRole,
            boolean requestedEnabled) {
        boolean removesEnabledAdmin = current.getRole() == UserRole.ADMIN
                && current.isEnabled()
                && (requestedRole != UserRole.ADMIN || !requestedEnabled);
        if (removesEnabledAdmin
                && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new LastAdminException(
                    "The final enabled administrator cannot be disabled or demoted");
        }
    }

    private void ensureNotDeletingLastEnabledAdmin(AppUser user) {
        if (user.getRole() == UserRole.ADMIN
                && user.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new LastAdminException(
                    "The final enabled administrator cannot be deleted");
        }
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
