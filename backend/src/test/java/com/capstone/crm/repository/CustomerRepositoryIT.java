package com.capstone.crm.repository;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Customer counterpart to AppUserRepositoryIT: the only test that touches
 * a real PostgreSQL for the customer schema. Everything else runs on H2 in
 * PostgreSQL mode, which does not enforce the unique/check constraints below
 * the same way a real server does.
 *
 * Pinned to the local docker-compose database explicitly rather than reading
 * .env, because .env may point at Azure and a test run must never write to a
 * shared cloud database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://${LOCAL_DB_HOST:localhost}:${LOCAL_DB_PORT:5432}/${LOCAL_DB_NAME}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${LOCAL_DB_USER}",
        "spring.datasource.password=${LOCAL_DB_PASSWORD}"
})
@EnabledIf("localPostgresIsReachable")
class CustomerRepositoryIT {

    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;

    // Reads the same LOCAL_DB_PORT the datasource url above resolves, rather
    // than hard-coding 5432: something else may already be listening on 5432,
    // which would make a hard-coded check pass while the real target port
    // still has nothing running on it.
    static boolean localPostgresIsReachable() {
        int port = Integer.parseInt(System.getenv().getOrDefault("LOCAL_DB_PORT", "5432"));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (IOException notRunning) {
            return false;
        }
    }

    @Test
    void flywayAppliedTheCustomerMigrationToRealPostgres() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script = 'V2__customer.sql' AND success = true",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void seededCustomersAreReadableFromRealPostgres() {
        assertThat(customers.findById("CUS-1001"))
                .isPresent()
                .get()
                .extracting(Customer::getFullName, Customer::getStatus)
                .containsExactly("Amina Khan", CustomerStatus.ACTIVE);
    }

    // H2 in PostgreSQL mode does not necessarily reject the same values, so a
    // broken constraint could pass the unit suite and only fail once it
    // reaches a real server.
    @Test
    void emailUniquenessIsEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO customer (customer_id, full_name, email, status) VALUES (?, ?, ?, ?)",
                "CUS-9001", "Someone Else", "amina.khan@example.test", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusCheckConstraintRejectsAnUnknownValue() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO customer (customer_id, full_name, email, status) VALUES (?, ?, ?, ?)",
                "CUS-9002", "Rogue Row", "rogue@example.test", "VIP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
