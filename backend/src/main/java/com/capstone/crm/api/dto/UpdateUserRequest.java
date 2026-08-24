package com.capstone.crm.api.dto;

import com.capstone.crm.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email String email,
        @Size(min = 8) String newPassword,
        @NotNull UserRole role,
        @NotNull Boolean enabled
) {}