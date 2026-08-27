package com.capstone.crm.messaging;

import com.capstone.crm.messaging.consumer.InteractionEventHandler;
import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // Overridden to a prefixed name so this proves the wiring rather than
        // the default. The producer and the @KafkaListener read the same
        // property; if either went back to a constant it would use
        // crm.interaction.v1, which is not the topic created below, and the
        // handler would never be called.
        "crm.messaging.interaction-topic=" + InteractionMessagingIT.TOPIC
})
@EmbeddedKafka(partitions = 1, topics = InteractionMessagingIT.TOPIC)
class InteractionMessagingIT {

    static final String TOPIC = "studentNN.crm.interaction.v1";

    @Autowired
    private InteractionEventProducer producer;

    @MockBean
    private InteractionEventHandler eventHandler;

    @Test
    void publishesAndConsumesAVersionOneEventExactlyOnce() {
        InteractionEvent event = InteractionEventFixtures.interactionCreated();
        assertThat(producer.topic()).isEqualTo(TOPIC);

        producer.publish(event);
        producer.publish(event);
        verify(eventHandler, timeout(10_000).times(1)).handle(event);
    }
}
