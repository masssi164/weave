package com.massimotter.weave.backend.guest.api;

import java.util.List;

public record GuestAccessContractResponse(
        boolean enabled,
        String identityType,
        List<String> invitationStates,
        List<String> defaultDeniedCapabilities,
        List<String> explicitPolicyCapabilities,
        List<String> auditEventTypes,
        boolean silentlyMergedWithInternalUsers,
        boolean externalIdentityLinkingAudited) {
}
