package com.capstone.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "interaction")
public class Interaction {

    @Id
    @Column(name = "interaction_id", nullable = false, length = 40)
    private String interactionId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "notes", nullable = false, length = 2_000)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected Interaction() {}

    public Interaction(String interactionId, String customerId, String channel,
                       String notes, Instant occurredAt) {
        this.interactionId = interactionId;
        this.customerId = customerId;
        this.channel = channel;
        this.notes = notes;
        this.occurredAt = occurredAt;
    }

    public String getInteractionId() { return interactionId; }
    public String getCustomerId() { return customerId; }
    public String getChannel() { return channel; }
    public String getNotes() { return notes; }
    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Interaction other)) return false;
        return interactionId != null && interactionId.equals(other.interactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interactionId);
    }

    @Override
    public String toString() {
        return "Interaction{interactionId='" + interactionId + "', customerId='"
                + customerId + "', channel='" + channel + "'}";
    }
}
