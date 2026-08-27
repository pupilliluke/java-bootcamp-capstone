package com.capstone.crm.messaging.consumer;

import com.capstone.crm.messaging.event.InteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingInteractionEventHandler implements InteractionEventHandler {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingInteractionEventHandler.class);

    @Override
    public void handle(InteractionEvent event) {
        // actor is logged; notes are not. The audit line answers who did what to
        // which customer, correlated -- without echoing the note contents the
        // event still carries (ADR-010).
        log.info(
                "Processed interaction event: eventId={}, correlationId={}, interactionId={}, customerId={}, channel={}, actor={}",
                event.eventId(),
                event.correlationId(),
                event.interactionId(),
                event.customerId(),
                event.channel(),
                event.actor()
        );
    }
}