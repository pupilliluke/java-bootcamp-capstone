package com.capstone.crm.service;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.Interaction;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import com.capstone.crm.repository.CustomerRepository;
import com.capstone.crm.repository.InteractionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "crm.demo.customer-count=3",
        "crm.demo.seed-interactions=true",
        "spring.datasource.url=jdbc:h2:mem:demo_interaction_seeder;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext
class DemoInteractionSeederTest {

    @Autowired DemoInteractionSeeder seeder;
    @Autowired CustomerRepository customers;
    @Autowired InteractionRepository interactions;

    @MockBean InteractionEventProducer producer;

    @Test
    void seedsFourRecentRowsForRealCustomersOnceWithoutPublishing() throws Exception {
        assertThat(customers.findById("CUS-1000"))
                .isPresent()
                .get()
                .extracting(Customer::getFullName, Customer::getEmail)
                .containsExactly("Joe mama", "joe.mama@example.test");

        assertThat(interactions.findAll())
                .extracting(
                        Interaction::getInteractionId,
                        Interaction::getCustomerId,
                        Interaction::getChannel,
                        Interaction::getNotes,
                        Interaction::getCreatedBy)
                .containsExactlyInAnyOrder(
                        tuple(DemoInteractionSeeder.AMINA_CHAT_ID, "CUS-1001", "CHAT",
                                "Confirmed billing address via chat", "demo-seeder"),
                        tuple(DemoInteractionSeeder.AMINA_EMAIL_ID, "CUS-1001", "EMAIL",
                                "Sent renewal quote and pricing breakdown", "demo-seeder"),
                        tuple(DemoInteractionSeeder.JOE_PHONE_ID, "CUS-1000", "PHONE",
                                "helloooooooo youe", "demo-seeder"),
                        tuple(DemoInteractionSeeder.AMINA_PHONE_ID, "CUS-1001", "PHONE",
                                "lab-request-001 demo interaction", "demo-seeder"));

        assertThat(interactions.findAll())
                .extracting(Interaction::getOccurredAt)
                .allSatisfy(occurredAt -> assertThat(occurredAt)
                        .isAfter(Instant.now().minusSeconds(2 * 60 * 60))
                        .isBeforeOrEqualTo(Instant.now()));

        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(interactions.count()).isEqualTo(4);
        verifyNoInteractions(producer);
    }
}
