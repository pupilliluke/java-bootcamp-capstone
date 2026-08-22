package com.capstone.crm.messaging.consumer;

public class UnsupportedEventVersionException extends RuntimeException {
    public UnsupportedEventVersionException(String message) {
        super(message);
    }
}