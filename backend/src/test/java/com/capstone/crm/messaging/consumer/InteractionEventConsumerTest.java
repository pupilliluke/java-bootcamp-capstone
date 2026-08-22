package com.capstone.crm.messaging.consumer;

import com.capstone.crm.messaging.InteractionEventFixtures;
import com.capstone.crm.messaging.event.InteractionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionEventConsumerTest {

    @Mock
    private ProcessedEventStore processedEventStore;

    @Mock
    private InteractionEventHandler eventHandler;

    @Test
    void processesANewEvent() {
        InteractionEvent event = InteractionEventFixtures.interactionCreated();
        when(processedEventStore.markIfNew(event.eventId())).thenReturn(true);
        InteractionEventConsumer consumer = new InteractionEventConsumer(processedEventStore, eventHandler);

        consumer.consume(event);
        verify(processedEventStore).markIfNew(event.eventId());
        verify(eventHandler).handle(event);
    }

    @Test
    void ignoresADuplicateEventSoTheBusinessEffectHappensOnlyOnce() {
        InteractionEvent event = InteractionEventFixtures.interactionCreated();
        when(processedEventStore.markIfNew(event.eventId())).thenReturn(true, false);
        InteractionEventConsumer consumer = new InteractionEventConsumer(processedEventStore, eventHandler);

        consumer.consume(event);
        consumer.consume(event);
        verify(processedEventStore, org.mockito.Mockito.times(2)).markIfNew(event.eventId());
        verify(eventHandler).handle(event);
        verifyNoMoreInteractions(eventHandler);
    }
}
