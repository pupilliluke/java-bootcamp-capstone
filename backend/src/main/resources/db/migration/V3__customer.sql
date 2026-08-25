-- Customer records. Kept on an assigned business key (e.g. CUS-1001) rather
-- than a surrogate id, since the REST path, the Kafka message key and
-- InteractionEvent.customerId all already address a customer by this value.
CREATE TABLE customer (
    customer_id VARCHAR(20)  PRIMARY KEY,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(30),
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PROSPECT', 'CLOSED'))
);
