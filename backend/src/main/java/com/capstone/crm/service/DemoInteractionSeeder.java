package com.capstone.crm.service;

import com.capstone.crm.entity.Interaction;
import com.capstone.crm.repository.InteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Persists a small, honest demo activity feed after the demo customers exist.
 *
 * <p>These rows go straight to the repository on purpose. Calling
 * {@link InteractionService#createAndPublish} during startup would announce
 * synthetic activity on Kafka every time the application starts. Fixed IDs
 * make the database side idempotent instead: an existing seed row is left
 * untouched, including its original occurrence time.
 */
@Component
@Order(2)
public class DemoInteractionSeeder implements ApplicationRunner {

    static final String AMINA_CHAT_ID = "INT-DEMO-AMINA-CHAT";
    static final String AMINA_EMAIL_ID = "INT-DEMO-AMINA-EMAIL";
    static final String JOE_PHONE_ID = "INT-DEMO-JOE-PHONE";
    static final String AMINA_PHONE_ID = "INT-DEMO-AMINA-PHONE";

    private static final Logger log = LoggerFactory.getLogger(DemoInteractionSeeder.class);
    private static final String SEEDED_BY = "demo-seeder";

    private final InteractionRepository interactionRepository;
    private final boolean enabled;

    public DemoInteractionSeeder(
            InteractionRepository interactionRepository,
            @Value("${crm.demo.seed-interactions:true}") boolean enabled) {
        this.interactionRepository = interactionRepository;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        Instant now = Instant.now();
        List<Interaction> missing = List.of(
                        interaction(AMINA_CHAT_ID, "CUS-1001", "CHAT",
                                "Confirmed billing address via chat", now.minus(Duration.ofMinutes(5))),
                        interaction(AMINA_EMAIL_ID, "CUS-1001", "EMAIL",
                                "Sent renewal quote and pricing breakdown", now.minus(Duration.ofMinutes(20))),
                        interaction(JOE_PHONE_ID, "CUS-1000", "PHONE",
                                "helloooooooo youe", now.minus(Duration.ofMinutes(59))),
                        interaction(AMINA_PHONE_ID, "CUS-1001", "PHONE",
                                "lab-request-001 demo interaction", now.minus(Duration.ofMinutes(83))))
                .stream()
                .filter(interaction -> !interactionRepository.existsById(interaction.getInteractionId()))
                .toList();

        if (missing.isEmpty()) {
            return;
        }

        interactionRepository.saveAll(missing);
        log.info("Seeded {} demo interactions", missing.size());
    }

    private static Interaction interaction(
            String interactionId, String customerId, String channel, String notes, Instant occurredAt) {
        return new Interaction(
                interactionId,
                customerId,
                channel,
                notes,
                occurredAt,
                "seed-" + interactionId,
                SEEDED_BY);
    }
}
