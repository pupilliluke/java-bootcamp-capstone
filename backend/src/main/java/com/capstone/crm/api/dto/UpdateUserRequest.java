package com.capstone.crm.api.dto;

import com.capstone.crm.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^(?:|.{8,72})$",
                message = "must be blank or between 8 and 72 characters")
        String newPassword,
        @NotNull UserRole role,
        @NotNull Boolean enabled
) {}
