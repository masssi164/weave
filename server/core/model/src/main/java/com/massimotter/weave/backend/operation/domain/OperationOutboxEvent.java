package com.massimotter.weave.backend.operation.domain;

import java.time.Instant;

public record OperationOutboxEvent(
        String outboxRef,
        String operationRef,
        String eventType,
        String payloadJson,
        Instant createdAt) {

    public OperationOutboxEvent {
        if (outboxRef == null || outboxRef.isBlank()
                || operationRef == null || operationRef.isBlank()
                || eventType == null || eventType.isBlank()
                || payloadJson == null || payloadJson.isBlank()
                || createdAt == null) {
            throw new IllegalArgumentException("complete outbox event data is required");
        }
    }
}
