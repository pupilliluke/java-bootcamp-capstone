package com.capstone.crm.api.dto;

import com.capstone.crm.entity.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

//required fields and phone
public record CustomerRequestDTO(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be 120 characters or fewer")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be 255 characters or fewer")
        String email,

        @Size(max = 30, message = "Phone must be 30 characters or fewer")
        String phone,

        @NotNull(message = "Status is required")
        CustomerStatus status
) {}
