package com.capstone.crm.messaging.consumer;

public class InvalidInteractionEventException extends RuntimeException {
    public InvalidInteractionEventException(String message) {
        super(message);
    }
}