package com.massimotter.weave.contract.mcp;

import java.util.List;

public record MemberMcpDomainContract(
        String contractVersion,
        String domain,
        String label,
        List<String> readCapabilities,
        List<String> writeCapabilities,
        List<String> canonicalObjectKinds,
        boolean policyEvaluatedBeforeProviderAccess,
        boolean unknownCapabilitiesFailClosed,
        boolean normalMembersConfigureProviders) {

    public MemberMcpDomainContract {
        contractVersion = text(contractVersion, "contractVersion");
        domain = text(domain, "domain");
        label = text(label, "label");
        readCapabilities = List.copyOf(readCapabilities == null ? List.of() : readCapabilities);
        writeCapabilities = List.copyOf(writeCapabilities == null ? List.of() : writeCapabilities);
        canonicalObjectKinds = List.copyOf(canonicalObjectKinds == null ? List.of() : canonicalObjectKinds);
        policyEvaluatedBeforeProviderAccess = true;
        unknownCapabilitiesFailClosed = true;
        normalMembersConfigureProviders = false;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
