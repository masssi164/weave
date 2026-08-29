package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.RunnerAuthenticatedPrincipal;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity.RunnerAuthenticationException;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.http.RunnerLiveRegistrationService;
import com.massimotter.weave.backend.runner.http.RunnerLiveRegistrationService.HeartbeatCommand;
import com.massimotter.weave.backend.runner.http.RunnerPublicCapabilityBundleVerifier.PublicBundleRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** X.509-only HTTP adapters for public Runner capability publication and liveness. */
@RestController
public class RunnerLiveRegistrationController {

    public static final String BUNDLE_PATH = "/runner/v1/capability-bundle";
    public static final String HEARTBEAT_PATH = "/runner/v1/heartbeat";

    private final RunnerLiveRegistrationService service;

    public RunnerLiveRegistrationController(RunnerLiveRegistrationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @PutMapping(BUNDLE_PATH)
    public ResponseEntity<Void> publish(
            @AuthenticationPrincipal RunnerAuthenticatedPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PublicBundleRequest request) {
        requireIdempotencyKey(idempotencyKey);
        RunnerAuthenticatedPrincipal authenticated = requirePrincipal(principal);
        try {
            var result = service.publish(authenticated.identity(), request);
            return ResponseEntity.noContent()
                    .header("Cache-Control", "no-store")
                    .header("X-Weave-Catalog-Revision", Long.toString(result.catalogRevision()))
                    .build();
        } catch (RunnerAuthenticationException failure) {
            throw unauthorized();
        } catch (IllegalStateException failure) {
            throw conflict("runner-capability-conflict", failure);
        } catch (IllegalArgumentException failure) {
            throw unprocessable("invalid-public-capability-bundle", failure);
        }
    }

    @PostMapping(HEARTBEAT_PATH)
    public ResponseEntity<Void> heartbeat(
            @AuthenticationPrincipal RunnerAuthenticatedPrincipal principal,
            @RequestBody RunnerHeartbeatRequest request) {
        RunnerAuthenticatedPrincipal authenticated = requirePrincipal(principal);
        try {
            var result = service.heartbeat(authenticated.identity(), request.toCommand());
            return ResponseEntity.noContent()
                    .header("Cache-Control", "no-store")
                    .header("X-Weave-Available-Slots", Integer.toString(result.availableSlots()))
                    .build();
        } catch (RunnerAuthenticationException failure) {
            throw unauthorized();
        } catch (IllegalStateException failure) {
            throw conflict("runner-heartbeat-conflict", failure);
        } catch (IllegalArgumentException failure) {
            throw unprocessable("invalid-runner-heartbeat", failure);
        }
    }

    private static RunnerAuthenticatedPrincipal requirePrincipal(
            RunnerAuthenticatedPrincipal principal) {
        if (principal == null) {
            throw unauthorized();
        }
        return principal;
    }

    private static void requireIdempotencyKey(String value) {
        if (value == null
                || value.length() < 16
                || value.length() > 256
                || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "invalid-idempotency-key",
                    "Idempotency-Key must contain 16 to 256 non-whitespace characters.",
                    Map.of());
        }
    }

    private static ApiErrorException unauthorized() {
        return new ApiErrorException(
                HttpStatus.UNAUTHORIZED,
                "runner-certificate-required",
                "A valid active Runner client certificate is required.",
                Map.of());
    }

    private static ApiErrorException conflict(String code, RuntimeException failure) {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                code,
                supportSafe(failure),
                Map.of());
    }

    private static ApiErrorException unprocessable(String code, RuntimeException failure) {
        return new ApiErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                code,
                supportSafe(failure),
                Map.of());
    }

    private static String supportSafe(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "Runner registration validation failed.";
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }

    public record RunnerHeartbeatRequest(
            String runnerId,
            String runnerVersion,
            String bundleDigest,
            int runningTasks,
            int capacity,
            Instant observedAt) {

        HeartbeatCommand toCommand() {
            Objects.requireNonNull(observedAt, "observedAt");
            return new HeartbeatCommand(
                    new RunnerId(runnerId),
                    runnerVersion,
                    bundleDigest,
                    runningTasks,
                    capacity);
        }
    }
}
