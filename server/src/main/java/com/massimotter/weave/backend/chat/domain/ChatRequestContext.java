package com.massimotter.weave.backend.chat.domain;

public record ChatRequestContext(
        String tenantId,
        String contextId,
        String identityIssuer,
        ChatActorRef actorRef,
        String authorizationPrincipalRef) {

    public ChatRequestContext {
        tenantId = required(tenantId, "chat tenant", 160);
        contextId = required(contextId, "chat context", 160);
        identityIssuer = required(identityIssuer, "identity issuer", 512);
        if (actorRef == null) {
            throw new IllegalArgumentException("chat actor is required");
        }
        authorizationPrincipalRef = required(
                authorizationPrincipalRef, "authorization principal", 255);
    }

    public ChatRequestContext(
            String tenantId,
            String contextId,
            String identityIssuer,
            ChatActorRef actorRef) {
        this(tenantId, contextId, identityIssuer, actorRef, actorRef == null ? null : actorRef.value());
    }

    public ChatRequestContext(String tenantId, String identityIssuer, ChatActorRef actorRef) {
        this(tenantId, "context-isolated-test", identityIssuer, actorRef,
                actorRef == null ? null : actorRef.value());
    }

    public static ChatRequestContext isolatedTest(ChatActorRef actorRef) {
        return new ChatRequestContext(
                "tenant-isolated-test", "context-isolated-test", "issuer-isolated-test", actorRef,
                actorRef == null ? null : actorRef.value());
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
