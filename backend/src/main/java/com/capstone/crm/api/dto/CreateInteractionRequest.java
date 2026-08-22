package com.capstone.crm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInteractionRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotBlank(message = "channel is required")
        @Size(max = 50)
        String channel,

        @NotBlank(message = "notes are required")
        @Size(max = 2_000)
        String notes
) {}