package com.capstone.crm.messaging.consumer;

import com.capstone.crm.entity.ProcessedEvent;
import com.capstone.crm.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Backed by the processed_event table (V5__processed_event.sql) rather than
// the ConcurrentHashMap this replaces, so "already handled this event" survives
// a consumer restart and holds across more than one running replica.
@Component
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventRepository processedEventRepository;

    public JpaProcessedEventStore(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Override
    public boolean markIfNew(UUID eventId) {
        if (processedEventRepository.existsById(eventId)) {
            return false;
        }
        try {
            processedEventRepository.save(new ProcessedEvent(eventId));
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            // Two consumer instances (or a redelivered record) both reached
            // the check above before either inserted. The primary key is the
            // real guard; this catch is just how the loser finds out it lost.
            return false;
        }
    }
}
