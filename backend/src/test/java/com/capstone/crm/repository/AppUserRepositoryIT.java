package com.capstone.crm.repository;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
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
 * The only test that touches a real PostgreSQL. Everything else runs on H2 in
 * PostgreSQL mode, which is an emulation: it does not enforce every constraint
 * the same way, so a migration can pass there and fail against the real server.
 *
 * Pinned to the local docker-compose database explicitly rather than reading
 * .env, because .env may point at Azure and a test run must never write to a
 * shared cloud database.
 */
@SpringBootTest(properties = {
        // Resolved from .env, the same keys docker-compose uses, so this cannot
        // drift from whatever the developer's container was actually created
        // with. Pinned to localhost explicitly rather than activating the local
        // profile, because .env may also carry Azure values and a test run must
        // never write to a shared cloud database.
        "spring.datasource.url=jdbc:postgresql://${LOCAL_DB_HOST:localhost}:${LOCAL_DB_PORT:5432}/${LOCAL_DB_NAME}",
        // The driver has to be overridden too. src/test/resources pins it to H2
        // for the rest of the suite, and a leftover org.h2.Driver rejects a
        // postgresql:// URL outright.
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${LOCAL_DB_USER}",
        "spring.datasource.password=${LOCAL_DB_PASSWORD}"
})
@EnabledIf("localPostgresIsReachable")
class AppUserRepositoryIT {

    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    // Checked before the Spring context is built, so a developer without the
    // container running gets a skip rather than a broken build. A skip is not a
    // pass: run `docker compose up -d postgres` before `mvn verify` to get the
    // real coverage.
    static boolean localPostgresIsReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 500);
            return true;
        } catch (IOException notRunning) {
            return false;
        }
    }

    @Test
    void flywayAppliedTheMigrationToRealPostgres() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void seededAccountsAreReadableByUsernameAndEmail() {
        assertThat(users.findByUsername("admin1"))
                .isPresent()
                .get()
                .extracting(AppUser::getRole)
                .isEqualTo(UserRole.ADMIN);

        assertThat(users.findByEmailIgnoreCase("AGENT1@EXAMPLE.TEST")).isPresent();
    }

    // The check constraint is the reason this test exists. H2 in PostgreSQL mode
    // does not necessarily reject the same values, so an invalid role could pass
    // the unit suite and only fail once it reaches a real server.
    @Test
    void roleCheckConstraintRejectsAnUnknownValue() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_user (username, email, role) VALUES (?, ?, ?)",
                "rogue", "rogue@example.test", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usernameUniquenessIsEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_user (username, email, role) VALUES (?, ?, ?)",
                "admin1", "duplicate@example.test", "AGENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
