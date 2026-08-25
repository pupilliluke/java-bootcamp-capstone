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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

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

    /**
     * Accounts awaiting approval — the self-registered ones that cannot sign in
     * yet. Oldest first so the queue is worked in the order people joined it.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listPending() {
        return userRepository.findByEnabledFalseAndRoleOrderByCreatedAtAsc(UserRole.AGENT).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Approves a pending sign-up. Idempotent: enabling an already-enabled user is
     * a no-op that still returns the account, so a double-clicked Approve button
     * cannot turn into an error.
     *
     * Refuses anything that is not an AGENT. Re-enabling a suspended
     * administrator is a different decision with different consequences, and it
     * belongs in the user editor where the actor has to choose the role
     * explicitly - not behind a button labelled "approve pending accounts".
     */
    @Transactional
    public UserResponse enable(Long userId, String actingUsername) {
        AppUser user = findUser(userId);
        if (user.getRole() != UserRole.AGENT) {
            throw new SelfUserModificationException(
                    "Only agent accounts can be approved here; use the user editor to change an administrator");
        }
        if (!user.isEnabled()) {
            user.setEnabled(true);
            user = userRepository.save(user);
            // Granting access should leave a name behind, not just a timestamp.
            // Without this the harmless action (registering) is logged and the
            // privilege-granting one is not.
            log.info("account_enabled actor={} user={} role={}",
                    actingUsername, user.getUsername(), user.getRole());
        }
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
