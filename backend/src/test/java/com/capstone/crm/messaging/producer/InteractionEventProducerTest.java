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

    // A prefixed name rather than the default, on purpose. If the topic is ever
    // hardcoded back into the producer the send goes to crm.interaction.v1 and
    // this fails, which is the regression worth catching -- the default is the
    // one value that would let a collision through unnoticed on the shared
    // broker.
    private static final String TOPIC = "studentNN.crm.interaction.v1";

    @Mock
    private KafkaTemplate<String, InteractionEvent> kafkaTemplate;

    @Captor
    private ArgumentCaptor<InteractionEvent> eventCaptor;

    @Test
    void publishesVersionOneEventToTheConfiguredTopicUsingCustomerIdAsTheKey() {
        InteractionEvent event = InteractionEventFixtures.interactionCreated();
        InteractionEventProducer producer =
                new InteractionEventProducer(kafkaTemplate, TOPIC);

        producer.publish(event);
        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(event.customerId()),
                eventCaptor.capture());
        assertThat(producer.topic()).isEqualTo(TOPIC);
        assertThat(eventCaptor.getValue()).isEqualTo(event);
        assertThat(eventCaptor.getValue().version()).isEqualTo(1);
    }
}
