-- Customer records. Kept on an assigned business key (e.g. CUS-1001) rather
-- than a surrogate id, since the REST path, the Kafka message key and
-- InteractionEvent.customerId all already address a customer by this value.
--
-- Portable SQL, like V1 and V2: the same file runs on PostgreSQL in normal
-- operation and on H2 in PostgreSQL mode under test.
CREATE TABLE customer (
    -- 100, matching interaction.customer_id in V2. A foreign key needs both
    -- sides to agree, and V2 says in as many words that "a later customer-table
    -- migration can add the FK" -- which is this one. Narrower would have made
    -- that promise unkeepable, and the browser journey already mints ids of
    -- around 17 characters against what used to be a 20-character ceiling.
    customer_id VARCHAR(100) PRIMARY KEY,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(30),
    status      VARCHAR(20)  NOT NULL,
    -- WITH TIME ZONE, matching app_user.created_at and interaction.occurred_at.
    -- Without it this is the one table whose timestamps mean different instants
    -- depending on the server's zone, which only shows up when two records
    -- written minutes apart sort into the wrong order after a deployment moves.
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PROSPECT', 'CLOSED')),
    -- Ids are upper case, and the database is what makes that true rather than
    -- a convention everyone remembers. customer_id is a case-sensitive primary
    -- key, so without this "cus-1006" and "CUS-1006" are two different
    -- customers: two rows, two URLs, and interactions logged under one spelling
    -- invisible under the other. Worse for Kafka, where customer_id is the
    -- message key -- one customer's events would split across partitions and
    -- lose the ordering guarantee the key exists to provide.
    --
    -- A CHECK rather than a unique index on LOWER(customer_id), because H2 does
    -- not support indexes over an expression and this migration has to run
    -- there too. CustomerService upper-cases ids on the way in, so this
    -- constraint is the backstop, not the mechanism.
    CONSTRAINT ck_customer_id_upper CHECK (customer_id = UPPER(customer_id))
);
