package com.capstone.crm.messaging.consumer;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markIfNew(UUID eventId) {
        return processedEventIds.add(eventId);
    }
}