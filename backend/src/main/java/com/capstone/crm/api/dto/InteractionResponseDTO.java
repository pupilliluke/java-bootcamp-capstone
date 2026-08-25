package com.capstone.crm.api.dto;

import java.time.Instant;

public record InteractionResponseDTO(
        String interactionId,
        String customerId,
        String channel,
        String notes,
        Instant occurredAt
) {}
