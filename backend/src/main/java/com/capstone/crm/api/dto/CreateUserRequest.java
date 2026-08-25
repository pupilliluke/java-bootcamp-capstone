package com.capstone.crm.api.dto;

import com.capstone.crm.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "must contain only letters, numbers, dots, underscores, or hyphens")
        String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role
) {}
