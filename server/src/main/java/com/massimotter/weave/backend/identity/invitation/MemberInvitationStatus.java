package com.massimotter.weave.backend.identity.invitation;

public enum MemberInvitationStatus {
    PENDING,
    SENT,
    ACCEPTED,
    EXPIRED,
    REVOKED,
    DELIVERY_FAILED
}
