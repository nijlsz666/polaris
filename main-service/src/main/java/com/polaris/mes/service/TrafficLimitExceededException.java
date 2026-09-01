package com.polaris.mes.service;

/** Raised when a tenant has no remaining traffic for the current request. */
public class TrafficLimitExceededException extends RuntimeException {
    public TrafficLimitExceededException() {
        super("租户流量已用尽，请联系总管理员分配流量");
    }

    public TrafficLimitExceededException(String message) {
        super(message);
    }
}
