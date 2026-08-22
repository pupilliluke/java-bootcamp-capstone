package com.capstone.crm.messaging.consumer;

import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.capstone.crm.messaging.consumer.InteractionEventHandler;

@Component
public class InteractionEventConsumer {

    private final ProcessedEventStore processedEventStore;
    private final InteractionEventHandler eventHandler;

    public InteractionEventConsumer(
            ProcessedEventStore processedEventStore,
            InteractionEventHandler eventHandler
    ) {
        this.processedEventStore = processedEventStore;
        this.eventHandler = eventHandler;
    }

    @KafkaListener(topics = InteractionEventProducer.TOPIC)
    public void consume(InteractionEvent event) {
        validate(event);
        if (!processedEventStore.markIfNew(event.eventId())) {
            return;
        }
        eventHandler.handle(event);
    }

    private void validate(InteractionEvent event) {
        if (event.eventId() == null
                || event.customerId() == null
                || event.eventType() == null) {
            throw new InvalidInteractionEventException(
                    "Interaction event has required fields missing");
        }

        if (event.version() != 1) {
            throw new UnsupportedEventVersionException(
                    "Unsupported interaction event version: " + event.version());
        }
    }
}