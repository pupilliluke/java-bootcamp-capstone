-- Customer ids are assigned by the server now rather than typed in by whoever
-- creates the customer. A sequence rather than making customer_id itself an
-- IDENTITY column: customer_id is a VARCHAR business key (CUS-1001, ...), not
-- the numeric type IDENTITY would require, so the sequence supplies the
-- number and the application formats it onto the "CUS-" prefix.
--
-- Starts at 1003: CUS-1001 and CUS-1002 are the seeded demo customers, created
-- directly by DemoCustomerSeeder rather than through this sequence, so the
-- first customer created through the API becomes CUS-1003.
CREATE SEQUENCE customer_number_seq START WITH 1003;
