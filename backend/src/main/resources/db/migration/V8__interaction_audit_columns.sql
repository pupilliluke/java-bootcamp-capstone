-- Two audit columns captured at create time, both nullable because rows written
-- before this migration recorded neither. Every new insert populates both.
--
--   correlation_id  ties the row back to the request that made it and the event
--                   that carried it (issue #88). The id was already stamped by
--                   CorrelationIdFilter, printed in logs, and put on the topic;
--                   it just never reached the row, so Lab 50's durability SELECT
--                   (WHERE customer_id = ? AND correlation_id = ?) could not run.
--   created_by      the authenticated user who logged the interaction (issue
--                   #86) -- the actor the event now also carries.
--
-- Portable SQL, matching V1's note: the same migration runs against Postgres in
-- production and H2 in PostgreSQL mode under test.
ALTER TABLE interaction ADD COLUMN correlation_id VARCHAR(100);
ALTER TABLE interaction ADD COLUMN created_by     VARCHAR(100);

-- The durability proof reads by customer and correlation id; index the pair so
-- that SELECT stays cheap as the table grows.
CREATE INDEX ix_interaction_customer_correlation
    ON interaction (customer_id, correlation_id);
