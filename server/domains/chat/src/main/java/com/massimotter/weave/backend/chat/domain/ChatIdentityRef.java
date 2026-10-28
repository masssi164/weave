package com.massimotter.weave.backend.chat.domain;

/** Immutable, tenant-scoped canonical human identity reference. */
public record ChatIdentityRef(
        String tenantId,
        String identityIssuer,
        ChatActorRef actorRef) {

    public ChatIdentityRef {
        tenantId = required(tenantId, "chat tenant", 160);
        identityIssuer = required(identityIssuer, "identity issuer", 512);
        if (actorRef == null) {
            throw new IllegalArgumentException("chat actor is required");
        }
    }

    public static ChatIdentityRef from(ChatRequestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("chat request context is required");
        }
        return new ChatIdentityRef(
                context.tenantId(),
                context.identityIssuer(),
                context.actorRef());
    }

    public ChatRequestContext requestContext() {
        return new ChatRequestContext(tenantId, identityIssuer, actorRef);
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
