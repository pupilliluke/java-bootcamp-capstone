package com.capstone.crm.messaging;

import com.capstone.crm.messaging.consumer.InteractionEventHandler;
import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = InteractionEventProducer.TOPIC)
class InteractionMessagingIT {

    @Autowired
    private InteractionEventProducer producer;

    @MockBean
    private InteractionEventHandler eventHandler;

    @Test
    void publishesAndConsumesAVersionOneEventExactlyOnce() {
        InteractionEvent event = InteractionEventFixtures.interactionCreated();
        producer.publish(event);
        producer.publish(event);
        verify(eventHandler, timeout(10_000).times(1)).handle(event);
    }
}
