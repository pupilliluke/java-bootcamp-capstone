package com.capstone.crm.messaging.producer;

import com.capstone.crm.messaging.event.InteractionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InteractionEventProducer {

    private final KafkaTemplate<String, InteractionEvent> kafkaTemplate;

    // Injected rather than a constant, because the broker on the course cluster
    // is shared by the whole cohort with no per-user topic isolation, and every
    // team is building this same CRM from the same brief. A hardcoded
    // crm.interaction.v1 means four teams publishing into one topic and reading
    // each other's interactions -- which does not fail, it just quietly produces
    // wrong answers, the worst way for this to go wrong.
    //
    // See crm.messaging.interaction-topic in application.yml for the name and
    // the default.
    private final String topic;

    public InteractionEventProducer(
            KafkaTemplate<String, InteractionEvent> kafkaTemplate,
            @Value("${crm.messaging.interaction-topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    // Exposed so a test can assert where events actually went without
    // reconstructing the property, and so the name appears in one place only.
    public String topic() {
        return topic;
    }

    public void publish(InteractionEvent event) {
        kafkaTemplate.send(topic, event.customerId(), event);
    }
}
