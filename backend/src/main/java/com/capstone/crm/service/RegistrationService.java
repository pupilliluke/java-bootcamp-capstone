package com.capstone.crm.service;

import com.capstone.crm.api.dto.RegisterRequest;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.exception.DuplicateUserException;
import com.capstone.crm.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public self-service sign-up. Kept separate from {@link AdminUserService} on
 * purpose: this one is reachable anonymously, so the two roles it may create,
 * the fields it accepts and the state it leaves an account in are all fixed here
 * rather than shared with the administrative path that can set anything.
 *
 * <p>An account created here is always a disabled AGENT. It cannot sign in until
 * an administrator enables it — the refusal is enforced by Spring Security, which
 * reads the flag through {@code CrmUserDetailsService}.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();

        if (users.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUserException("Username is already in use: " + username);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException("Email is already in use: " + email);
        }

        AppUser user = new AppUser(
                username,
                email,
                passwordEncoder.encode(request.password()),
                UserRole.AGENT);
        // The constructor enables new accounts, which is right for an admin
        // creating one and wrong here: a self-registered account waits for
        // approval, so the flag is cleared before the row is written.
        user.setEnabled(false);

        AppUser saved = users.save(user);
        // Username only. The email is personal data and the password never
        // reaches a log in any form.
        log.info("registration_pending_approval user={}", saved.getUsername());

        return new UserResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRole(),
                saved.isEnabled(),
                saved.getCreatedAt());
    }
}
