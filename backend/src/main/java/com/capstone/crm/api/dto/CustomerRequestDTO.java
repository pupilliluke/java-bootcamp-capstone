package com.capstone.crm.api.dto;

import com.capstone.crm.entity.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


// Every limit here matches a column in V3__customer.sql, and that is the point
// rather than belt-and-braces. While customers lived in a ConcurrentHashMap
// nothing rejected an over-long id or a null status. A real table does, and the
// rejection arrives as a DataIntegrityViolationException, which
// GlobalExceptionHandler reports as 409 "Username or email is already in use" —
// a message about the wrong entity, for the wrong reason, on a request that is
// simply too long. Validating here turns each of those into a 400 that names
// the field.
public record CustomerRequestDTO(
        @NotBlank(message = "Customer ID is required")
        @Size(max = 20, message = "Customer ID must be 20 characters or fewer")
        String customerId,

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
