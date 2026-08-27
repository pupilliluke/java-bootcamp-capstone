-- The interaction channel becomes a closed set. PHONE, EMAIL and CHAT because
-- that is what the frontend's Channel union has always sent -- every existing
-- row was written through the UI, so this is the one value set the constraint
-- can adopt without rewriting data. The lab's CALL/EMAIL/NOTE/MEETING set was
-- considered and rejected for exactly that reason (issue #82).
--
-- The DTO's @Pattern is the friendly door; this is the lock. Anything that
-- bypasses the API -- a future bulk import, a consumer writing rows, a psql
-- session -- hits the same rule. Deliberately unnamed columns only, portable
-- SQL: the suite applies this to H2 in PostgreSQL mode, so the CHECK is
-- enforced under test as well as in production.
--
-- If this migration fails on an existing database, a row holds a value outside
-- the set. That is a finding, not an inconvenience: fix the row, do not widen
-- the list.
ALTER TABLE interaction
    ADD CONSTRAINT ck_interaction_channel
    CHECK (channel IN ('PHONE', 'EMAIL', 'CHAT'));
