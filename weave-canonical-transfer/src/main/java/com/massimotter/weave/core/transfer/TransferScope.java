package com.massimotter.weave.core.transfer;

/** Provider-independent organization and collaboration scope selected for a transfer. */
public record TransferScope(
        String organizationId,
        CanonicalObjectRef.Domain domain,
        String collaborationScope) {

    public TransferScope {
        organizationId = TransferValidation.requireText(organizationId, "organizationId");
        domain = TransferValidation.require(domain, "domain");
        collaborationScope = TransferValidation.requireText(
                collaborationScope, "collaborationScope");
    }
}
