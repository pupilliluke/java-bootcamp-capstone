package com.capstone.crm.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record InteractionEvent(
        UUID eventId,
        String correlationId,
        String eventType,
        int version,
        Instant occurredAt,
        String customerId,
        String interactionId,
        String channel,
        String notes
) {}