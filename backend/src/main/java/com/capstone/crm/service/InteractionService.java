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
    public InteractionResponseDTO createAndPublish(
            CreateInteractionRequest request, String correlationId, String actor) {
        //ensures no misses by capitalization
        String customerId = customerService.get(request.customerId()).customerId();
        String interactionId = "INT-" + UUID.randomUUID();
        Instant occurredAt = Instant.now();

        // The correlation id and the actor are both received, not derived -- the
        // filter stamped the id and the security context named the user before
        // this method ran. Persisting them here is what lets the durability
        // SELECT (customer_id + correlation_id) survive a restart (issue #88) and
        // records who logged the interaction (issue #86).
        Interaction interaction = interactionRepository.save(new Interaction(
                interactionId,
                customerId,
                request.channel(),
                request.notes(),
                occurredAt,
                correlationId,
                actor
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
                request.notes(),
                actor
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
