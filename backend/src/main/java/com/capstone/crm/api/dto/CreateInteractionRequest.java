package com.capstone.crm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInteractionRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        // The closed set the frontend's Channel union already promises
        // (frontend/src/types/customer.ts) and ck_interaction_channel in
        // V7 enforces at the database. A @Pattern on a String rather than a
        // Java enum in the DTO: an enum mismatch dies inside Jackson as an
        // unreadable body, while a pattern failure flows through the same
        // MethodArgumentNotValidException path as every other field error,
        // so the caller gets "channel - must be ..." instead of a parse error.
        // Exact case on purpose -- the database CHECK is case-sensitive, and
        // accepting "email" here would store a row the constraint refuses.
        @NotBlank(message = "channel is required")
        @Pattern(regexp = "PHONE|EMAIL|CHAT",
                message = "must be one of PHONE, EMAIL, CHAT")
        String channel,

        @NotBlank(message = "notes are required")
        @Size(max = 2_000)
        String notes
) {}