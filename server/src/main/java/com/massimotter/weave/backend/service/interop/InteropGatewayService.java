package com.massimotter.weave.backend.service.interop;

import com.massimotter.weave.backend.config.InteropGatewayProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.interop.CanonicalBridgeEventResponse;
import com.massimotter.weave.backend.model.interop.InteropStatusResponse;
import com.massimotter.weave.backend.model.interop.SlackEventRequest;
import com.massimotter.weave.backend.model.interop.SlackOAuthCallbackRequest;
import com.massimotter.weave.backend.model.interop.SlackOutboundMessageRequest;
import com.massimotter.weave.backend.model.interop.SlackOutboundMessageResponse;
import com.massimotter.weave.backend.model.interop.SlackStatusResponse;
import com.massimotter.weave.backend.model.interop.TeamsContractResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InteropGatewayService {

    private final InteropGatewayProperties properties;
    private final IdempotencyKeyService idempotencyKeyService;
    private final SlackSignatureVerifier slackSignatureVerifier;
    private final SlackSecretResolver slackSecretResolver;

    public InteropGatewayService(
            InteropGatewayProperties properties,
            IdempotencyKeyService idempotencyKeyService,
            SlackSignatureVerifier slackSignatureVerifier,
            SlackSecretResolver slackSecretResolver) {
        this.properties = properties;
        this.idempotencyKeyService = idempotencyKeyService;
        this.slackSignatureVerifier = slackSignatureVerifier;
        this.slackSecretResolver = slackSecretResolver;
    }

    public InteropStatusResponse status() {
        return new InteropStatusResponse(
                properties.enabled(),
                properties.enabled() ? "degraded" : "unavailable",
                List.of(slackConnection(), teamsConnection()),
                new InteropStatusResponse.ConsentCapabilityRegistryResponse(List.of(
                        new InteropStatusResponse.CapabilityResponse("chat.message.read", "Read selected provider channel messages.", true),
                        new InteropStatusResponse.CapabilityResponse("chat.message.write", "Write bridged text messages into selected provider channels.", true),
                        new InteropStatusResponse.CapabilityResponse("workspace.mapping.manage", "Manage one provider-channel to Weave-room mapping.", false))),
                List.of("install", "scope_change", "mapping_change", "disconnect", "failure"),
                List.of("normal", "rate_limited", "quota_exhausted", "retry_after"),
                List.of("disabled", "missing_consent", "missing_mapping", "credentials_unavailable", "signature_invalid"),
                new InteropStatusResponse.SupportBundlePolicyResponse(
                        properties.supportBundleRedactionMode(),
                        true,
                        List.of("clientSecret", "clientSecretRef", "signingSecret", "signingSecretRef", "token", "tokenRef")),
                new InteropStatusResponse.ConnectorBoundarySummaryResponse(
                        false,
                        "internal-only until Slack and Teams prove the abstractions",
                        List.of("No public marketplace.", "No secrets in manifests.", "Provider credentials are referenced, never serialized.")));
    }

    public SlackStatusResponse slackStatus() {
        InteropGatewayProperties.Provider slack = properties.slack();
        boolean oauthConfigured = hasText(slack.clientId()) && hasText(slack.clientSecretRef());
        boolean signingConfigured = hasText(slack.signingSecretRef());
        boolean tokenRefConfigured = hasText(slack.tokenRef());
        boolean mappingConfigured = hasText(slack.channelId()) && hasText(slack.roomId());
        boolean ready = properties.enabled() && slack.enabled() && oauthConfigured && signingConfigured
                && tokenRefConfigured && mappingConfigured;
        return new SlackStatusResponse(
                properties.enabled() && slack.enabled(),
                ready ? "up" : (properties.enabled() && slack.enabled() ? "degraded" : "disabled"),
                ready ? "ready" : "unavailable",
                oauthConfigured,
                signingConfigured,
                tokenRefConfigured,
                mappingConfigured,
                false,
                List.of("rate_limited", "missing_mapping", "missing_consent", "signature_invalid", "delivery_degraded", "loop_prevented"),
                slackActions(oauthConfigured, signingConfigured, tokenRefConfigured, mappingConfigured));
    }

    public TeamsContractResponse teamsContract() {
        return new TeamsContractResponse(
                properties.enabled() && properties.teams().enabled(),
                true,
                "gated",
                List.of(
                        "Interop core status/audit vocabulary is merged.",
                        "Slack one-channel sandbox proves idempotency, loop prevention, consent, and mapping boundaries.",
                        "Microsoft Graph consent, subscription renewal, throttling, and delivery failure contracts are tested."),
                List.of("expired_subscription", "graph_throttled", "consent_changed", "delivery_failed"));
    }

    public CanonicalBridgeEventResponse ingestSlackEvent(SlackEventRequest request) {
        return ingestSlackEvent(request, null, null, null);
    }

    public CanonicalBridgeEventResponse ingestSlackEvent(
            SlackEventRequest request,
            String rawBody,
            String requestTimestamp,
            String requestSignature) {
        SlackStatusResponse status = slackStatus();
        if (!status.enabled()) {
            throw disabled("slack-events");
        }
        verifySlackSignature(rawBody, requestTimestamp, requestSignature);
        if (!status.channelMappingConfigured()) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "slack-mapping-unavailable",
                    "Slack bridge mapping is not configured.",
                    Map.of("module", "interop", "provider", "slack", "operation", "ingest-event"));
        }
        if ("bot_message".equals(request.subtype())) {
            throw new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "slack-loop-prevented",
                    "Slack bridge ignored a provider event that appears to come from a bridged bot message.",
                    Map.of("module", "interop", "provider", "slack", "operation", "ingest-event"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", request.text());
        payload.put("externalEventId", request.eventId());
        payload.put("channelId", request.channelId());
        payload.put("threadTs", request.threadTs());
        payload.put("loopPrevention", "bot_message events are rejected before mapping");
        return new CanonicalBridgeEventResponse(
                idempotencyKeyService.key("slack:event", request.teamId() + ":" + request.eventId()),
                "slack",
                "chat.message.text",
                "inbound",
                request.teamId(),
                properties.slack().roomId(),
                "slack:user:" + request.userId(),
                payload,
                true);
    }

    public SlackOutboundMessageResponse sendSlackMessage(SlackOutboundMessageRequest request) {
        SlackStatusResponse status = slackStatus();
        if (!status.enabled()) {
            throw disabled("slack-send-message");
        }
        if (!status.channelMappingConfigured()) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "slack-mapping-unavailable",
                    "Slack bridge mapping is not configured.",
                    Map.of("module", "interop", "provider", "slack", "operation", "send-message"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", request.text());
        payload.put("threadTs", request.threadTs());
        payload.put("loopPrevention", "outbound sandbox does not call Slack and uses idempotency before delivery");
        return new SlackOutboundMessageResponse(
                idempotencyKeyService.key("weave:slack:message", properties.slack().channelId() + ":" + request.eventId()),
                "slack",
                properties.slack().workspaceId(),
                properties.slack().channelId(),
                "sandbox-not-delivered",
                true,
                false,
                payload);
    }

    public Map<String, Object> oauthCallback(SlackOAuthCallbackRequest request) {
        if (!slackStatus().enabled()) {
            throw disabled("oauth-callback");
        }
        return Map.of(
                "provider", "slack",
                "installed", false,
                "credentialStored", false,
                "message", "Slack OAuth skeleton is wired; token exchange remains disabled until secret brokering is configured.");
    }

    private void verifySlackSignature(String rawBody, String requestTimestamp, String requestSignature) {
        if (rawBody == null && requestTimestamp == null && requestSignature == null) {
            return;
        }
        String signingSecretRef = properties.slack().signingSecretRef();
        if (!hasText(rawBody) || !hasText(requestTimestamp) || !hasText(requestSignature) || !hasText(signingSecretRef)) {
            throw signatureInvalid("missing Slack signature metadata");
        }
        String signingSecret = slackSecretResolver.resolveSigningSecret(signingSecretRef)
                .orElseThrow(() -> signatureInvalid("Slack signing secret reference is not resolvable"));
        if (!slackSignatureVerifier.verify(signingSecret, requestTimestamp, rawBody, requestSignature)) {
            throw signatureInvalid("Slack request signature did not verify");
        }
    }

    private ApiErrorException signatureInvalid(String reason) {
        return new ApiErrorException(
                HttpStatus.UNAUTHORIZED,
                "slack-signature-invalid",
                "Slack request signature could not be verified.",
                Map.of("module", "interop", "provider", "slack", "operation", "ingest-event", "reason", reason));
    }

    private InteropStatusResponse.ExternalConnectionResponse slackConnection() {
        SlackStatusResponse slack = slackStatus();
        return new InteropStatusResponse.ExternalConnectionResponse(
                "slack",
                slack.status(),
                slack.readiness(),
                slack.tokenReferenceConfigured() ? "reference-configured" : "missing-reference",
                slack.channelMappingConfigured() ? "one-channel-mapped" : "unmapped",
                List.of("oauth-install-skeleton", "signature-verification", "one-channel-text-sandbox", "idempotency"),
                slack.actions());
    }

    private InteropStatusResponse.ExternalConnectionResponse teamsConnection() {
        return new InteropStatusResponse.ExternalConnectionResponse(
                "teams",
                "disabled",
                "gated",
                "not-configured",
                "unmapped",
                List.of("status-contract-only"),
                List.of("Keep Teams disabled until Slack hardening proves the bridge abstractions."));
    }

    private List<String> slackActions(boolean oauthConfigured, boolean signingConfigured,
            boolean tokenRefConfigured, boolean mappingConfigured) {
        List<String> actions = new java.util.ArrayList<>();
        if (!properties.enabled()) {
            actions.add("Enable WEAVE_INTEROP_ENABLED only after operator review; default is disabled.");
        }
        if (!properties.slack().enabled()) {
            actions.add("Enable WEAVE_INTEROP_SLACK_ENABLED only for the Slack sandbox/on-ramp runtime.");
        }
        if (!oauthConfigured) {
            actions.add("Configure Slack client id and secret reference; never place raw client secrets in status output.");
        }
        if (!signingConfigured) {
            actions.add("Configure a Slack signing secret reference before accepting events.");
        }
        if (!tokenRefConfigured) {
            actions.add("Configure an encrypted Slack bot token reference before outbound delivery.");
        }
        if (!mappingConfigured) {
            actions.add("Configure exactly one Slack channel to Weave room mapping for this proof slice.");
        }
        return List.copyOf(actions);
    }

    private ApiErrorException disabled(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "interop-gateway-disabled",
                "Interop gateway is disabled by default and will not call external providers.",
                Map.of("module", "interop", "operation", operation, "productionCallsAllowed", false));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
