-- Evidence for ix_customer_status (issue #48 / lab 38).
-- Runs in a throwaway database so the dev `crm` database is untouched.

\echo '===================== SCHEMA (V3__customer.sql, table only) ====================='
CREATE TABLE customer (
    customer_id VARCHAR(100) PRIMARY KEY,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(30),
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PROSPECT', 'CLOSED')),
    CONSTRAINT ck_customer_id_upper CHECK (customer_id = UPPER(customer_id))
);

-- 50,000 rows at the status mix DemoCustomerSeeder produces: 60% ACTIVE,
-- 20% PROSPECT, 10% SUSPENDED, 10% CLOSED.
INSERT INTO customer (customer_id, full_name, email, status, created_at)
SELECT 'CUS-' || (100000 + g),
       'Load Test ' || g,
       'load' || g || '@example.test',
       (ARRAY['ACTIVE','ACTIVE','ACTIVE','ACTIVE','ACTIVE','ACTIVE',
              'PROSPECT','PROSPECT','SUSPENDED','CLOSED'])[1 + (g % 10)],
       NOW() - (g || ' hours')::interval
FROM generate_series(1, 50000) g;

ANALYZE customer;

\echo ''
\echo '===================== ROW MIX ====================='
SELECT status, count(*), round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS pct
FROM customer GROUP BY status ORDER BY 2 DESC;

-- Warm the cache so the numbers below compare plans, not disk luck.
SELECT count(*) FROM customer;

\echo ''
\echo '########################### BEFORE ix_customer_status ###########################'
\echo ''
\echo '--- default list: GET /api/customers  ->  status IN (ACTIVE, SUSPENDED, PROSPECT)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT customer_id, created_at, email, full_name, phone, status
FROM customer WHERE status IN ('ACTIVE','SUSPENDED','PROSPECT');

\echo ''
\echo '--- ?status=CLOSED  ->  status IN (CLOSED)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT customer_id, created_at, email, full_name, phone, status
FROM customer WHERE status IN ('CLOSED');

\echo ''
\echo '===================== V5__customer_status_index.sql ====================='
CREATE INDEX ix_customer_status ON customer (status);
ANALYZE customer;

\echo ''
\echo '########################### AFTER ix_customer_status ###########################'
\echo ''
\echo '--- default list: GET /api/customers  ->  status IN (ACTIVE, SUSPENDED, PROSPECT)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT customer_id, created_at, email, full_name, phone, status
FROM customer WHERE status IN ('ACTIVE','SUSPENDED','PROSPECT');

\echo ''
\echo '--- ?status=CLOSED  ->  status IN (CLOSED)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT customer_id, created_at, email, full_name, phone, status
FROM customer WHERE status IN ('CLOSED');

\echo ''
\echo '===================== INDEX SIZE ====================='
SELECT pg_size_pretty(pg_relation_size('customer')) AS table_size,
       pg_size_pretty(pg_relation_size('ix_customer_status')) AS index_size;
