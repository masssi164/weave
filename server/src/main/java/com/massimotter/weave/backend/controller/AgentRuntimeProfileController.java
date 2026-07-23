package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.agentruntime.adapter.AgentRuntimeWorkloadTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfileJwkSet;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeWorkloadTokenException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.agentruntime.FlattenedRuntimeProfileJwsResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent-runtime")
@ConditionalOnExpression(
        "'${weave.agent-runtime.workload-identity.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.profile-signing.enabled:false}' == 'true'")
public class AgentRuntimeProfileController {
    private static final MediaType JWK_SET = MediaType.parseMediaType("application/jwk-set+json");

    private final RuntimeProfileDeliveryService profiles;
    private final RuntimeProfileTrustBundlePublisher trustBundle;
    private final AgentRuntimeWorkloadTokenPolicy tokenPolicy;
    private final Clock clock;

    public AgentRuntimeProfileController(
            RuntimeProfileDeliveryService profiles,
            RuntimeProfileTrustBundlePublisher trustBundle,
            AgentRuntimeWorkloadTokenPolicy tokenPolicy) {
        if (profiles == null || trustBundle == null || tokenPolicy == null) {
            throw new IllegalArgumentException("Agent Runtime profile API dependencies are required");
        }
        this.profiles = profiles;
        this.trustBundle = trustBundle;
        this.tokenPolicy = tokenPolicy;
        this.clock = Clock.systemUTC();
    }

    @GetMapping(value = "/trust/jwks.json", produces = "application/jwk-set+json")
    public ResponseEntity<RuntimeProfileJwkSet> trustBundle() {
        RuntimeProfileJwkSet published = trustBundle.publish(Instant.now(clock))
                .orElseThrow(AgentRuntimeProfileController::trustUnavailable);
        return ResponseEntity.ok()
                .contentType(JWK_SET)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(published);
    }

    @GetMapping(value = "/runtime-profiles/{profileHash}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlattenedRuntimeProfileJwsResponse> runtimeProfile(
            @PathVariable String profileHash,
            JwtAuthenticationToken authentication) {
        RuntimeWorkloadPrincipal principal;
        try {
            principal = tokenPolicy.resolve(authentication.getToken());
        } catch (InvalidRuntimeWorkloadTokenException exception) {
            throw workloadForbidden();
        }
        FlattenedRuntimeProfileJwsResponse response = profiles.findCurrent(profileHash, principal)
                .map(FlattenedRuntimeProfileJwsResponse::from)
                .orElseThrow(AgentRuntimeProfileController::profileUnavailable);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    private static ApiErrorException workloadForbidden() {
        return new ApiErrorException(
                HttpStatus.FORBIDDEN,
                "agent-runtime-workload-forbidden",
                "The workload token does not satisfy the Agent Runtime profile-read contract.",
                Map.of());
    }

    private static ApiErrorException profileUnavailable() {
        return new ApiErrorException(
                HttpStatus.NOT_FOUND,
                "runtime-profile-unavailable",
                "The RuntimeProfile is unavailable for this workload.",
                Map.of());
    }

    private static ApiErrorException trustUnavailable() {
        return new ApiErrorException(
                HttpStatus.NOT_FOUND,
                "agent-runtime-trust-unavailable",
                "No Agent Runtime signing trust bundle is currently published.",
                Map.of());
    }
}
