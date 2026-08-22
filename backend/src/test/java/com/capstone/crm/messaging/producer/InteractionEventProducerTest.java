package com.capstone.crm.messaging.producer;

import com.capstone.crm.messaging.InteractionEventFixtures;
import com.capstone.crm.messaging.event.InteractionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InteractionEventProducerTest {

    @Mock
    private KafkaTemplate<String, InteractionEvent> kafkaTemplate;

    @Captor
    private ArgumentCaptor<InteractionEvent> eventCaptor;

    @Test
    void publishesVersionOneEventToTheVersionedTopicUsingCustomerIdAsTheKey() {
        InteractionEvent event = InteractionEventFixtures.interactionCreated();
        InteractionEventProducer producer = new InteractionEventProducer(kafkaTemplate);

        producer.publish(event);
        verify(kafkaTemplate).send(
                eq(InteractionEventProducer.TOPIC),
                eq(event.customerId()),
                eventCaptor.capture());
        assertThat(InteractionEventProducer.TOPIC).isEqualTo("crm.interaction.v1");
        assertThat(eventCaptor.getValue()).isEqualTo(event);
        assertThat(eventCaptor.getValue().version()).isEqualTo(1);
    }
}
