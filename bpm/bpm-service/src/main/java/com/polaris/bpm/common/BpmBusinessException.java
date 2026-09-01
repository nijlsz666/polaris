package com.polaris.bpm.common;

public class BpmBusinessException extends RuntimeException {
    public BpmBusinessException(String message) {
        super(message);
    }

    public BpmBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
