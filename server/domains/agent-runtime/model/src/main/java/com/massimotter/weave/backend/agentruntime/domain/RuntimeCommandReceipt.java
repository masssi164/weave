package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;

public record RuntimeCommandReceipt(
        String organizationRef,
        String personRef,
        String idempotencyKey,
        String command,
        Status status,
        String cellRef,
        Long runtimeVersion,
        String auditRef,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    public RuntimeCommandReceipt {
        RuntimeMemberBinding.requireText(organizationRef, "organizationRef");
        RuntimeMemberBinding.requireText(personRef, "personRef");
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey length must be between 16 and 128");
        }
        RuntimeMemberBinding.requireText(command, "command");
        if (status == null) {
            throw new IllegalArgumentException("command status is required");
        }
        RuntimeMemberBinding.requireText(cellRef, "cellRef");
        if (status == Status.COMPLETED && runtimeVersion == null) {
            throw new IllegalArgumentException("completed command requires runtimeVersion");
        }
        if (status == Status.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("failed command requires failureCode");
        }
        if (status != Status.FAILED && failureCode != null) {
            throw new IllegalArgumentException("only failed commands may carry failureCode");
        }
        RuntimeMemberBinding.requireText(auditRef, "auditRef");
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("command timestamps are invalid");
        }
    }

    public enum Status {
        STARTED,
        COMPLETED,
        FAILED
    }
}
