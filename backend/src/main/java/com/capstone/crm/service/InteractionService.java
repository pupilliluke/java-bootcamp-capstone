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
        // Ensures the interaction belongs to a known customer.
        customerService.get(request.customerId());
        String interactionId = "INT-" + UUID.randomUUID();
        Instant occurredAt = Instant.now();

        interactionRepository.save(new Interaction(
                interactionId,
                request.customerId(),
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
                request.customerId(), // Kafka message key
                interactionId,
                request.channel(),
                request.notes()
        );
        producer.publish(event);
        return event;
    }

    @Transactional(readOnly = true)
    public List<InteractionResponseDTO> listForCustomer(String customerId) {
        // A nested customer resource returns 404 when its parent is unknown.
        customerService.get(customerId);
        return interactionRepository
                .findByCustomerIdOrderByOccurredAtDescInteractionIdDesc(customerId)
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
