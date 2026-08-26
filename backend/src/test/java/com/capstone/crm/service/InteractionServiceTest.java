package com.capstone.crm.service;

import com.capstone.crm.api.dto.CreateInteractionRequest;
import com.capstone.crm.exception.CustomerNotFoundException;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import com.capstone.crm.repository.InteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What happens when the customer does not exist.
 *
 * <p>{@code interaction.customer_id} has no foreign key, so the check in
 * {@code createAndPublish} is the only thing standing between the API and an
 * interaction attached to a customer that was never created. That makes the
 * order of operations load-bearing rather than incidental, and nothing was
 * covering it: {@code InteractionControllerTest} tests the read path's 404, and
 * the write path had no test for an unknown customer at all.
 *
 * <p>The failure this guards against is quiet. Move the customer lookup below
 * the save during a refactor and every other test still passes — the
 * transaction rolls the row back, so persistence looks fine — while
 * {@code producer.publish} has already put an event on the topic for an
 * interaction that does not exist. Kafka has no rollback.
 */
@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    @Mock CustomerService customerService;
    @Mock InteractionRepository interactions;
    @Mock InteractionEventProducer producer;

    private InteractionService service;

    @BeforeEach
    void setUp() {
        service = new InteractionService(customerService, interactions, producer);
    }

    private static CreateInteractionRequest request() {
        return new CreateInteractionRequest("CUS-9999", "EMAIL", "Renewal follow-up");
    }

    @Test
    void anUnknownCustomerSavesNothingAndPublishesNothing() {
        when(customerService.get("CUS-9999"))
                .thenThrow(new CustomerNotFoundException("Customer not found: CUS-9999"));

        assertThatThrownBy(() -> service.createAndPublish(request(), "correlation-id"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("CUS-9999");

        // Both, not just the repository. A rolled-back save is invisible to a
        // caller; a published event is not, and it is the one that cannot be
        // taken back.
        verifyNoInteractions(interactions);
        verifyNoInteractions(producer);
    }

    @Test
    void readingHistoryForAnUnknownCustomerTouchesNoRepository() {
        when(customerService.get("CUS-9999"))
                .thenThrow(new CustomerNotFoundException("Customer not found: CUS-9999"));

        assertThatThrownBy(() -> service.listForCustomer("CUS-9999"))
                .isInstanceOf(CustomerNotFoundException.class);

        verifyNoInteractions(interactions);
    }
}
