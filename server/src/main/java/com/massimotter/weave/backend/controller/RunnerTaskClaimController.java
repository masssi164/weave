package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.RunnerAuthenticatedPrincipal;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimHttpResponse;
import com.massimotter.weave.backend.runner.application.RunnerTaskClaimService;
import com.massimotter.weave.backend.runner.application.RunnerTaskClaimService.ClaimCommand;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity.RunnerAuthenticationException;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** HTTP adapter for one authenticated and bounded private Runner task claim. */
@RestController
public class RunnerTaskClaimController {

    public static final String PATH = "/runner/v1/tasks:claim";

    private final RunnerTaskClaimService claimService;
    private final ObjectMapper objectMapper;

    public RunnerTaskClaimController(
            RunnerTaskClaimService claimService,
            ObjectMapper objectMapper) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @PostMapping(PATH)
    public ResponseEntity<?> claim(
            @AuthenticationPrincipal RunnerAuthenticatedPrincipal principal,
            @RequestHeader(name = "Prefer", required = false) List<String> preferHeaders,
            @Valid @RequestBody TaskClaimRequest request) {
        if (principal == null) {
            throw unauthorized();
        }
        try {
            ClaimHttpResponse<Lease> response = claimService.claim(
                    principal.identity(),
                    preferHeaders == null ? List.of() : List.copyOf(preferHeaders),
                    request.toCommand());
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
            response.headers().forEach((name, value) -> builder.header(name, value));
            if (response.body().isEmpty()) {
                return builder.build();
            }
            return builder.body(TaskLeaseResponse.from(
                    response.body().orElseThrow(),
                    objectMapper));
        } catch (RunnerAuthenticationException failure) {
            throw unauthorized();
        } catch (IllegalArgumentException failure) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "invalid-runner-claim",
                    "Runner claim request validation failed.",
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

    public record TaskClaimRequest(
            @NotBlank
            @Pattern(regexp = "^runner_[A-Za-z0-9_-]{8,128}$")
            String runnerId,
            @NotBlank
            @Pattern(regexp = "^sha256:[a-f0-9]{64}$")
            String bundleDigest,
            @NotEmpty
            @Size(max = 128)
            List<@Valid @NotNull CapabilityReferenceRequest> capabilities,
            @NotNull
            @Min(1)
            @Max(1024)
            Integer availableSlots) {

        public TaskClaimRequest {
            capabilities = capabilities == null ? null : List.copyOf(capabilities);
        }

        ClaimCommand toCommand() {
            LinkedHashSet<CapabilityRef> references = new LinkedHashSet<>();
            for (CapabilityReferenceRequest capability : capabilities) {
                references.add(capability.toReference());
            }
            if (references.size() != capabilities.size()) {
                throw new IllegalArgumentException("capabilities must be unique");
            }
            return new ClaimCommand(
                    new RunnerId(runnerId),
                    bundleDigest,
                    Set.copyOf(references),
                    availableSlots);
        }
    }

    public record CapabilityReferenceRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
            String id,
            @NotBlank
            @Pattern(regexp = "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$")
            String version) {

        CapabilityRef toReference() {
            return new CapabilityRef(new CapabilityId(id), version);
        }
    }

    public record TaskLeaseResponse(
            String schemaVersion,
            UUID taskId,
            UUID leaseId,
            long fencingToken,
            String runnerId,
            CapabilityReferenceResponse capability,
            String bundleDigest,
            int attempt,
            String idempotencyKey,
            JsonNode payload,
            JsonNode contextRefs,
            JsonNode resourceGrants,
            Instant issuedAt,
            Instant expiresAt,
            Instant deadline,
            String traceparent) {

        static TaskLeaseResponse from(Lease lease, ObjectMapper objectMapper) {
            return new TaskLeaseResponse(
                    "weave.runner.task-lease/v1",
                    lease.taskId(),
                    lease.leaseId(),
                    lease.fencingToken(),
                    lease.runnerId().value(),
                    new CapabilityReferenceResponse(
                            lease.capability().id().value(),
                            lease.capability().version()),
                    lease.bundleDigest(),
                    lease.attempt(),
                    lease.idempotencyKey(),
                    readJson(objectMapper, lease.payloadJson(), "payload"),
                    readJson(objectMapper, lease.contextRefsJson(), "contextRefs"),
                    readJson(objectMapper, lease.resourceGrantsJson(), "resourceGrants"),
                    lease.issuedAt(),
                    lease.expiresAt(),
                    lease.deadline(),
                    lease.traceparent());
        }

        private static JsonNode readJson(
                ObjectMapper objectMapper,
                String value,
                String field) {
            try {
                return objectMapper.readTree(value);
            } catch (JacksonException failure) {
                throw new IllegalStateException(
                        "persisted Runner lease contains invalid " + field,
                        failure);
            }
        }
    }

    public record CapabilityReferenceResponse(String id, String version) {}
}
