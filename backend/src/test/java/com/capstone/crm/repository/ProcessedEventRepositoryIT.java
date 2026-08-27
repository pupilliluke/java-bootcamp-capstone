package com.capstone.crm.repository;

import com.capstone.crm.entity.ProcessedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ProcessedEvent counterpart to CustomerRepositoryIT: the only test that
 * touches a real PostgreSQL for the idempotency table backing
 * JpaProcessedEventStore. Everything else runs on H2 in PostgreSQL mode,
 * which does not enforce the primary key the same way under a real race.
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
class ProcessedEventRepositoryIT {

    @Autowired ProcessedEventRepository processedEvents;
    @Autowired JdbcTemplate jdbc;

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
    void flywayAppliedTheProcessedEventMigrationToRealPostgres() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script = 'V5__processed_event.sql' AND success = true",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void savingTheSameEventIdTwiceViolatesThePrimaryKey() {
        UUID eventId = UUID.randomUUID();
        processedEvents.save(new ProcessedEvent(eventId));

        // H2 in PostgreSQL mode does not necessarily reject this the same way,
        // so a broken constraint could pass the unit suite and only fail once
        // it reaches a real server — the same reasoning as
        // CustomerRepositoryIT's emailUniquenessIsEnforced.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO processed_event (event_id) VALUES (?)", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aNewEventIdIsNotYetProcessed() {
        assertThat(processedEvents.existsById(UUID.randomUUID())).isFalse();
    }
}
