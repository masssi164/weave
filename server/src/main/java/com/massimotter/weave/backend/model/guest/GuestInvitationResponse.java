package com.massimotter.weave.backend.model.guest;

import java.util.List;

public record GuestInvitationResponse(
        String guestId,
        String state,
        String identityType,
        List<String> grantedCapabilities,
        boolean explicitPolicyRequired,
        boolean internalUserMerged) {
}
