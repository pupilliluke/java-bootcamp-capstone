package com.capstone.crm.service;

import com.capstone.crm.api.dto.RegisterRequest;
import com.capstone.crm.api.dto.RegistrationResponse;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.exception.DuplicateUserException;
import com.capstone.crm.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

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

    /**
     * Names the demo seeder depends on. {@code DemoUserSeeder} skips a username
     * that already exists and says nothing when it does, so without this an
     * anonymous caller could register "admin1" first and leave an environment
     * whose only administrator account belongs to a stranger.
     */
    private static final Set<String> RESERVED_USERNAMES =
            Set.of("admin1", "agent1", "admin", "root", "system");

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();

        // Both checks always run, and the reserved list is consulted either way.
        // Short-circuiting would make "username taken" measurably cheaper than
        // "email taken", and the difference is something an anonymous caller can
        // time from outside.
        boolean usernameTaken = users.existsByUsernameIgnoreCase(username)
                || RESERVED_USERNAMES.contains(username.toLowerCase(Locale.ROOT));
        boolean emailTaken = users.existsByEmailIgnoreCase(email);

        if (usernameTaken || emailTaken) {
            // One message covering both. A signup form has to admit that
            // something is taken, but naming which field turns the endpoint into
            // a way to check whether a particular person has an account here -
            // and the front end renders this message verbatim. The wording
            // matches what GlobalExceptionHandler returns for a unique-constraint
            // violation, so losing a race to a concurrent registration reads
            // exactly the same from outside.
            throw new DuplicateUserException("Username or email is already in use");
        }

        // The factory fixes role and enabled together, so there is no setter to
        // forget and no moment at which a row meant to await approval exists as
        // enabled.
        AppUser saved = users.save(AppUser.pendingApproval(
                username, email, passwordEncoder.encode(request.password())));

        // Username only. The email is personal data and the password never
        // reaches a log in any form.
        log.info("registration_pending_approval user={}", saved.getUsername());

        // Not UserResponse. That record carries the row id, and id is an identity
        // column: register twice, subtract the two, and an anonymous caller knows
        // how many accounts were created in between.
        return RegistrationResponse.pending(saved.getUsername());
    }
}
