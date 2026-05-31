package com.massimotter.weave.backend.service;

public class WeaverPaChatUnavailableException extends RuntimeException {

    public WeaverPaChatUnavailableException(String message) {
        super(message);
    }

    public WeaverPaChatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
