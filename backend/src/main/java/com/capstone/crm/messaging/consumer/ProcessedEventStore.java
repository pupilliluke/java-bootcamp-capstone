package com.capstone.crm.messaging.consumer;

import java.util.UUID;

public interface ProcessedEventStore {
    boolean markIfNew(UUID eventId);
}