package com.massimotter.weave.core.transfer;

import java.util.Set;

/** Versioned capabilities of one private southbound connector implementation. */
public record ConnectorDescriptor(
        String connectorKey,
        CanonicalObjectRef.Domain domain,
        String adapterProfileVersion,
        Set<Capability> capabilities) {

    public ConnectorDescriptor {
        connectorKey = TransferValidation.requireText(connectorKey, "connectorKey");
        domain = TransferValidation.require(domain, "domain");
        adapterProfileVersion = TransferValidation.requireText(
                adapterProfileVersion, "adapterProfileVersion");
        capabilities = Set.copyOf(TransferValidation.require(capabilities, "capabilities"));
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("at least one connector capability is required");
        }
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    public enum Capability {
        SOURCE_READ,
        TARGET_PREFLIGHT,
        TARGET_APPLY,
        TARGET_VERIFY,
        DELETE,
        READBACK,
        RESUME
    }
}
