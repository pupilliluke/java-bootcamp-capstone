package com.capstone.crm.service;

import com.capstone.crm.api.dto.LoginResponseDTO;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.exception.AccountPendingApprovalException;
import com.capstone.crm.exception.InvalidCredentialsException;
import com.capstone.crm.repository.AppUserRepository;
import com.capstone.crm.security.GoogleIdentity;
import com.capstone.crm.security.GoogleTokenVerifier;
import com.capstone.crm.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Google Sign-In. Verifies the ID token, resolves it to an {@link AppUser} by the
 * verified email, and — for a known, enabled account — issues the same access
 * token the password login returns, so everything downstream is unchanged.
 *
 * <p>Provisioning mirrors self-service sign-up: a first-time Google sign-in
 * creates a disabled AGENT with no password and then refuses the sign-in until an
 * administrator approves it. A Google account whose email is not verified is
 * never provisioned.
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private final GoogleTokenVerifier verifier;
    private final AppUserRepository users;
    private final JwtService jwtService;

    public GoogleAuthService(
            GoogleTokenVerifier verifier,
            AppUserRepository users,
            JwtService jwtService) {
        this.verifier = verifier;
        this.users = users;
        this.jwtService = jwtService;
    }

    // Deliberately NOT @Transactional. The provisioning branch saves the new
    // account and then throws to refuse the sign-in; under a single transaction
    // that RuntimeException would roll the insert back, so the account would be
    // "created and awaiting approval" in the message but never actually persist,
    // and an admin would have nothing to approve. Each repository.save() commits
    // on its own, which is what we want here — the method does at most one write,
    // so there is no wider atomicity to protect.
    public LoginResponseDTO authenticate(String idToken) {
        GoogleIdentity identity = verifier.verify(idToken);

        // A signed token can still carry an unverified email; provisioning on it
        // would let anyone who can set any address to a Google account seed an
        // account here under someone else's name.
        if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            log.warn("google_login_rejected reason=email_unverified");
            throw new InvalidCredentialsException("Google account email is not verified");
        }
        String email = identity.email().trim();

        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            // First sign-in. Reuse the self-service factory so the account is a
            // disabled AGENT with role and enabled fixed together; the email
            // doubles as the username, and the password hash is null because this
            // account authenticates through Google.
            AppUser created = users.save(AppUser.pendingApproval(email, email, null));
            // Email is personal data; log the surrogate username the factory set.
            log.info("google_signin_provisioned_pending user={}", created.getUsername());
            throw new AccountPendingApprovalException(
                    "Your account has been created and is awaiting administrator approval.");
        }

        if (!user.isEnabled()) {
            log.info("google_login_rejected reason=pending_approval user={}", user.getUsername());
            throw new AccountPendingApprovalException(
                    "Your account is awaiting administrator approval.");
        }

        String role = user.getRole().name();
        log.info("google_login_success user={} role={}", user.getUsername(), role);
        return new LoginResponseDTO(
                jwtService.issueToken(user.getUsername(), role),
                "Bearer",
                user.getUsername(),
                role);
    }
}
