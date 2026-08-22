package com.capstone.crm.messaging.producer;

import com.capstone.crm.messaging.event.InteractionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InteractionEventProducer {

    public static final String TOPIC = "crm.interaction.v1";
    private final KafkaTemplate<String, InteractionEvent> kafkaTemplate;

    public InteractionEventProducer(KafkaTemplate<String, InteractionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    public void publish(InteractionEvent event) {
        kafkaTemplate.send(TOPIC, event.customerId(), event);
    }
}