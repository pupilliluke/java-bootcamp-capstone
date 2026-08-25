package com.capstone.crm.api;

import com.capstone.crm.api.dto.LoginRequestDTO;
import com.capstone.crm.api.dto.LoginResponseDTO;
import com.capstone.crm.api.dto.RegisterRequest;
import com.capstone.crm.api.dto.RegistrationResponse;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.exception.InvalidCredentialsException;
import com.capstone.crm.security.JwtService;
import com.capstone.crm.service.RegistrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RegistrationService registrationService;

    public AuthController(
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            RegistrationService registrationService) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.registrationService = registrationService;
    }

    /**
     * Public sign-up. Returns 201 with the created account so the caller can see
     * {@code enabled: false} — the account exists but cannot sign in until an
     * administrator approves it.
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegistrationResponse created = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );
            String username = authentication.getName();
            String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            // The username and role are safe to log; the password and the issued
            // token are not, and never appear here.
            log.info("login_success user={} role={}", username, role);
            return ResponseEntity.ok(new LoginResponseDTO(
                    jwtService.issueToken(username, role),
                    "Bearer",
                    username,
                    role));
        } catch (AuthenticationException ex) {
            log.warn("login_failed user={}", request.username());
            throw new InvalidCredentialsException("Invalid credentials");
        }
    }
}
