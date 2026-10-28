package com.massimotter.weave.backend.chat.domain;

/**
 * IDM-resolved invitation target. Canonical identity remains immutable issuer+subject;
 * the separately verified policy principal is used only at the Context/ReBAC boundary.
 */
public record ChatResolvedIdentity(
        ChatIdentityRef identity,
        String authorizationPrincipalRef) {

    public ChatResolvedIdentity {
        if (identity == null) {
            throw new IllegalArgumentException("canonical Chat identity is required");
        }
        if (authorizationPrincipalRef == null || authorizationPrincipalRef.isBlank()
                || authorizationPrincipalRef.length() > 255) {
            throw new IllegalArgumentException("Chat authorization principal is invalid");
        }
        authorizationPrincipalRef = authorizationPrincipalRef.trim();
    }

    public static ChatResolvedIdentity from(ChatRequestContext context) {
        return new ChatResolvedIdentity(
                ChatIdentityRef.from(context),
                context.authorizationPrincipalRef());
    }

    public String tenantId() {
        return identity.tenantId();
    }

    public String identityIssuer() {
        return identity.identityIssuer();
    }

    public ChatActorRef actorRef() {
        return identity.actorRef();
    }

    public ChatRequestContext providerRequestContext() {
        return new ChatRequestContext(
                tenantId(), "context-isolated-test", identityIssuer(), actorRef(), authorizationPrincipalRef);
    }
}
