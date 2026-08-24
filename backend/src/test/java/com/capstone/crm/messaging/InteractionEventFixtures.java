package com.capstone.crm.messaging;

import com.capstone.crm.messaging.event.InteractionEvent;

import java.time.Instant;
import java.util.UUID;

public final class InteractionEventFixtures {

    private InteractionEventFixtures() {
    }
    public static InteractionEvent interactionCreated() {
        return new InteractionEvent(
                UUID.fromString("44d12c06-a817-4f79-b4ca-b0b07965c351"),
                "9d5f0e6a-4b8a-4b8a-9b8a-3f5c6a1e2d3b",
                "interaction.created",
                1,
                Instant.parse("2026-08-22T16:00:00Z"),
                "CUS-1001",
                "INT-1001",
                "EMAIL",
                "Sent a welcome email");
    }
}
