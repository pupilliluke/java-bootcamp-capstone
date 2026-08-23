package com.capstone.crm.api;

import com.capstone.crm.api.dto.LoginRequestDTO;
import com.capstone.crm.api.dto.LoginResponseDTO;
import com.capstone.crm.exception.InvalidCredentialsException;
import com.capstone.crm.security.CrmUserDetailsService;
import com.capstone.crm.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final CrmUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            JwtService jwtService,
            CrmUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(request.username());
            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new InvalidCredentialsException("Invalid credentials");
            }
            String role = user.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            return ResponseEntity.ok(new LoginResponseDTO(
                    jwtService.issueToken(request.username(), role),
                    "Bearer",
                    request.username(),
                    role));
        } catch (InvalidCredentialsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Unknown user and wrong password answer identically so the endpoint
            // cannot be used to enumerate valid usernames.
            throw new InvalidCredentialsException("Invalid credentials");
        }
    }
}
