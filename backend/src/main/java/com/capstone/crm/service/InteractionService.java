package com.capstone.crm.service;

import com.capstone.crm.api.dto.CreateInteractionRequest;
import com.capstone.crm.api.dto.InteractionResponseDTO;
import com.capstone.crm.entity.Interaction;
import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import com.capstone.crm.repository.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InteractionService {

    private final CustomerService customerService;
    private final InteractionRepository interactionRepository;
    private final InteractionEventProducer producer;

    public InteractionService(
            CustomerService customerService,
            InteractionRepository interactionRepository,
            InteractionEventProducer producer
    ) {
        this.customerService = customerService;
        this.interactionRepository = interactionRepository;
        this.producer = producer;
    }

    @Transactional
    public InteractionEvent createAndPublish(CreateInteractionRequest request, String correlationId) {
        // Ensures the interaction belongs to a known customer, and yields the
        // customer's own spelling of its id. Everything below stores that rather
        // than what the caller typed: a request for "cus-1002" resolves to
        // CUS-1002, so filing the row under the caller's version would hide it
        // from the read-back, which queries by exact match. The Kafka key has to
        // agree for the same reason — one customer, one partition.
        String customerId = customerService.get(request.customerId()).customerId();
        String interactionId = "INT-" + UUID.randomUUID();
        Instant occurredAt = Instant.now();

        interactionRepository.save(new Interaction(
                interactionId,
                customerId,
                request.channel(),
                request.notes(),
                occurredAt
        ));

        InteractionEvent event = new InteractionEvent(
                UUID.randomUUID(), // Kafka event ID
                correlationId,
                "interaction.created", // event type
                1, // contract version
                occurredAt,
                customerId, // Kafka message key
                interactionId,
                request.channel(),
                request.notes()
        );
        producer.publish(event);
        return event;
    }

    @Transactional(readOnly = true)
    public List<InteractionResponseDTO> listForCustomer(String customerId) {
        // A nested customer resource returns 404 when its parent is unknown, and
        // the lookup below uses the customer's canonical id for the same reason
        // the write path does.
        String canonicalId = customerService.get(customerId).customerId();
        return interactionRepository
                .findByCustomerIdOrderByOccurredAtDescInteractionIdDesc(canonicalId)
                .stream()
                .map(InteractionService::toResponse)
                .toList();
    }

    private static InteractionResponseDTO toResponse(Interaction interaction) {
        return new InteractionResponseDTO(
                interaction.getInteractionId(),
                interaction.getCustomerId(),
                interaction.getChannel(),
                interaction.getNotes(),
                interaction.getOccurredAt()
        );
    }
}
