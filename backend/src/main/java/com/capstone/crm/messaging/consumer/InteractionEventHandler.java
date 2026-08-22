package com.capstone.crm.messaging.consumer;

import com.capstone.crm.messaging.event.InteractionEvent;

public interface InteractionEventHandler {
    void handle(InteractionEvent event);
}