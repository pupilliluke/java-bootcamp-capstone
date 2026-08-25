-- Durable customer interactions. Customers are still held by the application's
-- in-memory repository on this branch, so customer_id is an indexed business
-- reference rather than a foreign key. The service validates the customer
-- before inserting; a later customer-table migration can add the FK.
CREATE TABLE interaction (
    interaction_id VARCHAR(40)  PRIMARY KEY,
    customer_id    VARCHAR(100) NOT NULL,
    channel        VARCHAR(50)  NOT NULL,
    notes          VARCHAR(2000) NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_interaction_customer_occurred
    ON interaction (customer_id, occurred_at);
