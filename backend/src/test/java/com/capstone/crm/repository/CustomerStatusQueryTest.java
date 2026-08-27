package com.capstone.crm.repository;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findByStatusIn on its own, away from the controller: what the derived query
 * returns for several statuses, for one, for a status nothing holds, and -- the
 * one that matters -- what it does not return.
 *
 * Two deliberate departures from the default @DataJpaTest setup, both about
 * which database this runs against:
 *
 *   * replace = NONE, so the datasource configured for tests is used rather
 *     than a plain embedded one. V3__customer.sql is written for PostgreSQL and
 *     the suite runs it on H2 in PostgreSQL mode; the default replacement drops
 *     MODE=PostgreSQL and the migration no longer applies cleanly.
 *   * its own database name. Every @SpringBootTest in the suite shares
 *     jdbc:h2:mem:crm, which by then holds the demo seed plus whatever rows the
 *     other test classes have committed. findByStatusIn reads committed rows,
 *     so on the shared database "returns empty" and "returns nothing we did not
 *     ask for" would be assertions about test ordering rather than about the
 *     query. Here the table starts empty and holds only these four rows --
 *     @DataJpaTest does not pick up DemoCustomerSeeder, which is a @Component.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:customer_status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CustomerStatusQueryTest {

    @Autowired CustomerRepository customers;
    @Autowired TestEntityManager entityManager;

    // No SUSPENDED row on purpose: aStatusNothingHoldsReturnsEmpty needs a
    // status that is valid but unused, and adding one here would quietly turn
    // that test into a duplicate of the others.
    @BeforeEach
    void seed() {
        persist("CUS-8001", CustomerStatus.ACTIVE);
        persist("CUS-8002", CustomerStatus.ACTIVE);
        persist("CUS-8003", CustomerStatus.PROSPECT);
        persist("CUS-8004", CustomerStatus.CLOSED);
        entityManager.flush();
    }

    @Test
    void severalStatusesAtOnceReturnsEveryMatchingCustomer() {
        assertThat(customers.findByStatusIn(EnumSet.of(CustomerStatus.ACTIVE, CustomerStatus.PROSPECT)))
                .extracting(Customer::getCustomerId)
                .containsExactlyInAnyOrder("CUS-8001", "CUS-8002", "CUS-8003");
    }

    @Test
    void oneStatusReturnsOnlyThatStatus() {
        assertThat(customers.findByStatusIn(EnumSet.of(CustomerStatus.CLOSED)))
                .extracting(Customer::getCustomerId)
                .containsExactly("CUS-8004");
    }

    @Test
    void aStatusNothingHoldsReturnsEmpty() {
        assertThat(customers.findByStatusIn(EnumSet.of(CustomerStatus.SUSPENDED))).isEmpty();
    }

    /**
     * The property the whole issue rests on, asked with the exact set
     * CustomerService uses when no status is requested: a customer whose status
     * was not named does not come back. A findByStatusIn that quietly ignored
     * its argument would pass every test above by returning everything.
     */
    @Test
    void doesNotReturnStatusesNobodyAskedFor() {
        assertThat(customers.findByStatusIn(EnumSet.complementOf(EnumSet.of(CustomerStatus.CLOSED))))
                .extracting(Customer::getStatus)
                .doesNotContain(CustomerStatus.CLOSED)
                .containsOnly(CustomerStatus.ACTIVE, CustomerStatus.PROSPECT);
    }

    // Upper case ids and an email derived from the id, because V3__customer.sql
    // declares both ck_customer_id_upper and uq_customer_email.
    private void persist(String customerId, CustomerStatus status) {
        entityManager.persist(new Customer(
                customerId,
                "Demo " + customerId,
                customerId.toLowerCase() + "@example.test",
                null,
                status,
                Instant.now()));
    }
}
