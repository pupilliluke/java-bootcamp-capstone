package com.capstone.crm.exception;

public class SelfUserModificationException extends RuntimeException {

    public SelfUserModificationException(String message) {
        super(message);
    }
}
