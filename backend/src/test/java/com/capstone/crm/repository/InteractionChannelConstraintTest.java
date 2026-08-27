package com.capstone.crm.repository;

import com.capstone.crm.entity.Interaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ck_interaction_channel (V7) at the database, away from the API. The DTO's
 * pattern guards one door; this proves the table itself refuses an unknown
 * channel, which is what stands between a future bulk import or consumer
 * write and a row the frontend's Channel union cannot represent.
 *
 * Same two departures from the default @DataJpaTest setup as
 * CustomerStatusQueryTest, for the same reasons: replace = NONE so Flyway's
 * migrations run against H2 in PostgreSQL mode -- the constraint under test
 * only exists if V7 applied -- and an isolated database name so nothing here
 * depends on rows the rest of the suite committed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:interaction_channel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class InteractionChannelConstraintTest {

    @Autowired TestEntityManager entityManager;

    @Test
    void everyAllowedChannelInserts() {
        for (String channel : new String[] {"PHONE", "EMAIL", "CHAT"}) {
            entityManager.persistAndFlush(interaction("INT-OK-" + channel, channel));
        }
        assertThat(entityManager.getEntityManager()
                .createQuery("select count(i) from Interaction i", Long.class)
                .getSingleResult()).isEqualTo(3);
    }

    @Test
    void anUnknownChannelIsRefusedByTheTable() {
        assertRefusedByTheConstraint("INT-BAD-1", "FAX");
    }

    // The API rejects lowercase before it ever reaches a table; this pins the
    // reason it must -- the constraint is case-sensitive, so a lowercase value
    // let through would fail here, deep in a transaction, instead of at the door.
    @Test
    void aLowercaseChannelIsRefusedByTheTable() {
        assertRefusedByTheConstraint("INT-BAD-2", "email");
    }

    // The constraint name surfaces on the JDBC exception at the bottom of the
    // cause chain, not on the PersistenceException Hibernate throws at the top,
    // and H2 reports it upper-cased. Walk the chain and compare loosely so the
    // assertion is about which constraint fired, not how a driver spells it.
    private void assertRefusedByTheConstraint(String id, String channel) {
        Throwable thrown = catchThrowable(() ->
                entityManager.persistAndFlush(interaction(id, channel)));
        assertThat(thrown).isNotNull();
        StringBuilder chain = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            chain.append(t.getMessage()).append('\n');
        }
        assertThat(chain.toString()).containsIgnoringCase("ck_interaction_channel");
    }

    private static Interaction interaction(String id, String channel) {
        return new Interaction(id, "CUS-1001", channel, "constraint probe", Instant.now());
    }
}
