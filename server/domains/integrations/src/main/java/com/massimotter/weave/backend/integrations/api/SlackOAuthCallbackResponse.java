package com.massimotter.weave.backend.integrations.api;

/** Support-safe result for the disabled Slack token-exchange skeleton. */
public record SlackOAuthCallbackResponse(
        String provider,
        boolean installed,
        boolean credentialStored,
        String message) {
}
