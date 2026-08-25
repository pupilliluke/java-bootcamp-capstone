package com.capstone.crm.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public sign-up payload. Deliberately has no role field: self-registration
 * always produces a disabled AGENT and the role is assigned server-side, so a
 * caller cannot promote itself by adding one.
 *
 * <p>The minimum length is 12 rather than the 8 an administrator may set on
 * {@link CreateUserRequest}: an admin-created account is vouched for by a human,
 * a self-registered one is not. The 72-byte ceiling is BCrypt's, which silently
 * truncates beyond it.
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "must contain only letters, numbers, dots, underscores, or hyphens")
        String username,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank @Size(min = 12, max = 72) String password
) {}
