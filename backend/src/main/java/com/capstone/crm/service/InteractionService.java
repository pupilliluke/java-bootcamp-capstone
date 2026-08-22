package com.capstone.crm.service;

import com.capstone.crm.api.dto.CreateInteractionRequest;
import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class InteractionService {

    private final CustomerService customerService;
    private final InteractionEventProducer producer;

    public InteractionService(
            CustomerService customerService,
            InteractionEventProducer producer
    ) {
        this.customerService = customerService;
        this.producer = producer;
    }

    public InteractionEvent createAndPublish(CreateInteractionRequest request) {
        // Ensures the interaction belongs to a known customer.
        customerService.get(request.customerId());
        InteractionEvent event = new InteractionEvent(
                UUID.randomUUID(), // Kafka event ID
                "interaction.created", // event type
                1, // contract version
                Instant.now(), // creation time
                request.customerId(), // Kafka message key
                "INT-" + UUID.randomUUID(), // interaction ID
                request.channel(),
                request.notes()
        );
        producer.publish(event);
        return event;
    }
}