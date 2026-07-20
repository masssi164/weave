package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService.AdminContext;
import com.massimotter.weave.backend.config.AgentRuntimeAdminSecurityConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeErrorResponseWriter;
import com.massimotter.weave.backend.model.agentruntime.AgentRuntimeProjectionResponse;
import com.massimotter.weave.backend.model.agentruntime.DeleteAgentRuntimeStateRequest;
import com.massimotter.weave.backend.model.agentruntime.RevokeAgentRuntimeRequest;
import com.massimotter.weave.backend.model.agentruntime.StopAgentRuntimeRequest;
import com.massimotter.weave.backend.model.agentruntime.SuspendAgentRuntimeRequest;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.OrganizationIdentityContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exact, organization-bound administrative API from the canonical ARC OpenAPI. */
@RestController
@Validated
@RequestMapping("/api/admin/agent-runtimes")
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'"
                + " && '${weave.agent-runtime.workload-identity.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.policy.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.profile-signing.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.state-store.enabled:false}' == 'true'")
@PreAuthorize(AgentRuntimeAdminSecurityConfiguration.ACCESS_EXPRESSION)
public class AgentRuntimeAdminController {
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String PERSON_REF = "acct_[a-f0-9]{32}";

    private final AgentRuntimeAdminService runtimes;
    private final AgentRuntimeErrorResponseWriter errors;

    public AgentRuntimeAdminController(
            AgentRuntimeAdminService runtimes,
            AgentRuntimeErrorResponseWriter errors) {
        this.runtimes = runtimes;
        this.errors = errors;
    }

    @GetMapping("/{personRef}")
    public ResponseEntity<AgentRuntimeProjectionResponse> get(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ok(runtimes.get(context(authentication, request), personRef));
    }

    @PostMapping("/{personRef}/provision")
    public ResponseEntity<AgentRuntimeProjectionResponse> provision(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return accepted(runtimes.provision(context(authentication, request), personRef, idempotencyKey));
    }

    @PostMapping("/{personRef}/start")
    public ResponseEntity<AgentRuntimeProjectionResponse> start(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return accepted(runtimes.start(context(authentication, request), personRef, idempotencyKey));
    }

    @PostMapping("/{personRef}/stop")
    public ResponseEntity<AgentRuntimeProjectionResponse> stop(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            @RequestBody(required = false) @Valid StopAgentRuntimeRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        StopAgentRuntimeRequest command = body == null ? StopAgentRuntimeRequest.graceful() : body;
        return accepted(runtimes.stop(
                context(authentication, request), personRef, idempotencyKey, command));
    }

    @PostMapping("/{personRef}/suspend")
    public ResponseEntity<AgentRuntimeProjectionResponse> suspend(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            @RequestBody @Valid SuspendAgentRuntimeRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return accepted(runtimes.suspend(
                context(authentication, request), personRef, idempotencyKey, body.reason()));
    }

    @PostMapping("/{personRef}/reconcile")
    public ResponseEntity<AgentRuntimeProjectionResponse> reconcile(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return accepted(runtimes.reconcile(context(authentication, request), personRef, idempotencyKey));
    }

    @PostMapping("/{personRef}/revoke")
    public ResponseEntity<AgentRuntimeProjectionResponse> revoke(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            @RequestBody @Valid RevokeAgentRuntimeRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return accepted(runtimes.revoke(
                context(authentication, request), personRef, idempotencyKey,
                body.reason(), body.entitlementRevision()));
    }

    @DeleteMapping("/{personRef}/runtime-state")
    public ResponseEntity<AgentRuntimeProjectionResponse> deleteRuntimeState(
            @PathVariable @Pattern(regexp = PERSON_REF) String personRef,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 16, max = 128) String idempotencyKey,
            @RequestBody @Valid DeleteAgentRuntimeStateRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return accepted(runtimes.deleteRuntimeState(
                context(authentication, request), personRef, idempotencyKey, body.reason()));
    }

    private AdminContext context(JwtAuthenticationToken authentication, HttpServletRequest request) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(authentication.getToken());
        return new AdminContext(
                identity.organizationId(), identity.primaryIdentityKey(), errors.auditRef(request));
    }

    private static ResponseEntity<AgentRuntimeProjectionResponse> ok(AgentRuntimeProjectionResponse body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(body);
    }

    private static ResponseEntity<AgentRuntimeProjectionResponse> accepted(AgentRuntimeProjectionResponse body) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(body);
    }
}
