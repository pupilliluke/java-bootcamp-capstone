package com.capstone.crm.service;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import com.capstone.crm.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//TODO: demo records only. Replace with real customer onboarding before any
// deployment that is not a training environment.
@Component
public class DemoCustomerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCustomerSeeder.class);

    // Cycled rather than drawn at random, so two runs of the seeder produce the
    // same table and an EXPLAIN taken on Monday is comparable with one taken on
    // Friday. Weighted to resemble a real book of business, which is also what
    // makes it useful evidence for ix_customer_status: 90% of rows are open, so
    // the default list reads most of the table while ?status=CLOSED reads a
    // tenth of it -- roughly where PostgreSQL starts preferring an index.
    private static final CustomerStatus[] STATUS_CYCLE = {
            CustomerStatus.ACTIVE, CustomerStatus.ACTIVE, CustomerStatus.ACTIVE,
            CustomerStatus.ACTIVE, CustomerStatus.ACTIVE, CustomerStatus.ACTIVE,
            CustomerStatus.PROSPECT, CustomerStatus.PROSPECT,
            CustomerStatus.SUSPENDED,
            CustomerStatus.CLOSED,
    };

    private static final String[] FIRST_NAMES = {
            "Amina", "Ravi", "Chen", "Sofia", "Kwame", "Yuki", "Omar", "Lena",
            "Diego", "Priya", "Noor", "Tomas", "Ingrid", "Hassan", "Mei",
            "Andre", "Fatima", "Jonas", "Aisha", "Pavel",
    };

    private static final String[] LAST_NAMES = {
            "Khan", "Singh", "Wei", "Marino", "Mensah", "Tanaka", "Haddad",
            "Novak", "Alvarez", "Patel", "Rahman", "Silva", "Larsen", "Osman",
            "Zhang", "Dubois", "Bakr", "Meyer", "Okafor", "Sokolov",
    };

    private final CustomerRepository customerRepository;
    private final int targetCount;

    public DemoCustomerSeeder(CustomerRepository customerRepository,
                              @Value("${crm.demo.customer-count:1000}") int targetCount) {
        this.customerRepository = customerRepository;
        this.targetCount = targetCount;
    }

    // Guarded by existsById rather than always saving, so a restart cannot
    // clobber a customer a demo session has since updated or closed.
    @Override
    public void run(ApplicationArguments args) {
        seed("CUS-1001", "Amina Khan", "amina.khan@example.test", "555-0101", CustomerStatus.ACTIVE);
        seed("CUS-1002", "Ravi Singh", "ravi.singh@example.test", "555-0102", CustomerStatus.ACTIVE);
        seedBulk();
    }

    private void seed(String customerId, String fullName, String email, String phone, CustomerStatus status) {
        if (customerRepository.existsById(customerId)) {
            return;
        }
        customerRepository.save(new Customer(customerId, fullName, email, phone, status, Instant.now()));
        log.info("Seeded demo customer {}", customerId);
    }

    /**
     * Tops the customer table up to crm.demo.customer-count rows. A list of two
     * is enough to click through but not to judge anything by: the list page
     * pages at eight rows, the status filter has nothing to filter, and an
     * EXPLAIN over two rows says only that PostgreSQL sequential scans small
     * tables.
     *
     * A target rather than a fixed number to add, so this is safe to run again.
     * A database that already holds the target is left alone, which is what
     * makes restarts idempotent, and one that has drifted below it -- a fresh
     * volume, or a demo session that deleted a few -- comes back up to size
     * with new ids rather than reusing the ones that went.
     *
     * Set crm.demo.customer-count (or CRM_DEMO_CUSTOMER_COUNT) to 0 to turn
     * this off, which is worth doing before pointing a run at anything shared.
     */
    private void seedBulk() {
        // One count() rather than an existsById per row: this runs on every
        // startup, and a thousand round trips is a lot to spend answering a
        // question one number settles.
        long missing = targetCount - customerRepository.count();
        if (missing <= 0) {
            return;
        }

        int toCreate = (int) missing;
        Instant start = Instant.now().minus(Duration.ofHours(8L * toCreate));
        List<Customer> batch = new ArrayList<>(toCreate);

        for (int i = 0; i < toCreate; i++) {
            // Ids come from customer_number_seq, the same source
            // CustomerService.create() draws from. Numbering these CUS-1003
            // upward by hand would leave the sequence pointing at 1003, and the
            // first customer created through the API would collide with a
            // seeded row -- a primary key violation surfacing as a confusing
            // 409 about an email that is not in use.
            String customerId = "CUS-" + customerRepository.nextCustomerNumber();
            String first = FIRST_NAMES[i % FIRST_NAMES.length];
            String last = LAST_NAMES[(i / FIRST_NAMES.length) % LAST_NAMES.length];

            batch.add(new Customer(
                    customerId,
                    first + " " + last,
                    // The id carries the uniqueness, because uq_customer_email
                    // is a constraint and 20x20 names over 1000 rows is not.
                    (first + "." + last + "." + customerId).toLowerCase(Locale.ROOT) + "@example.test",
                    String.format("555-%04d", i % 10_000),
                    STATUS_CYCLE[i % STATUS_CYCLE.length],
                    // Spread over the run-up to now rather than all stamped at
                    // startup, so created_at can be sorted or charted on and
                    // the dashboard's recent-customers table is not arbitrary.
                    start.plus(Duration.ofHours(8L * i))));
        }

        customerRepository.saveAll(batch);
        log.info("Seeded {} bulk demo customers ({} through {}), customer table now at {}",
                batch.size(), batch.get(0).getCustomerId(), batch.get(batch.size() - 1).getCustomerId(),
                targetCount);
    }
}
