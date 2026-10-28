package com.massimotter.weave.backend.identity.member.api;

/**
 * Dedicated protected assignment contract for the native
 * {@code /capabilities/weaver} organization group.
 */
public record WeaverEntitlementUpdateRequest(boolean entitled) {}
