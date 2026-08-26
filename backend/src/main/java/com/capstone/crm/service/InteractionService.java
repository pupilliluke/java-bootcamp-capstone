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
    public InteractionResponseDTO createAndPublish(CreateInteractionRequest request, String correlationId) {
        //ensures no misses by capitalization
        String customerId = customerService.get(request.customerId()).customerId();
        String interactionId = "INT-" + UUID.randomUUID();
        Instant occurredAt = Instant.now();

        Interaction interaction = interactionRepository.save(new Interaction(
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

        return toResponse(interaction);
    }

    @Transactional(readOnly = true)
    public List<InteractionResponseDTO> listForCustomer(String customerId) {
        //cannonicalId is all caps
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
