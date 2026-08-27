-- Tracks which Kafka interaction events have already been processed, so a
-- consumer restart or a second replica does not reprocess an event and repeat
-- its side effect. Replaces InMemoryProcessedEventStore, whose
-- ConcurrentHashMap only ever worked because exactly one consumer instance
-- had ever run and it was never restarted mid-stream.
--
-- The primary key is the enforcement mechanism, not the application: two
-- consumer instances racing on the same event id have one INSERT succeed and
-- one fail with a constraint violation, which JpaProcessedEventStore treats
-- as "already processed" rather than an error.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
