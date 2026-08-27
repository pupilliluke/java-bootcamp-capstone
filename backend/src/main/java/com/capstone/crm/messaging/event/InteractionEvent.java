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
        String notes,
        // The authenticated user who recorded the interaction (issue #86). Added
        // as an optional field, so per ADR-010's compatibility rule this stays
        // version 1: a v1 consumer that ignores unknown fields keeps working, and
        // one built against the new shape reads actor where present and null on
        // events published before this field existed.
        //
        // notes stays on the event deliberately -- see ADR-010's decision. The
        // topic is namespace-prefixed, so exposure is within one team's stream,
        // not the whole cohort's.
        String actor
) {}