package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import com.massimotter.weave.backend.model.identity.IdentitySessionReconcileResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Identity session", description = "Authenticated, provider-neutral identity bootstrap.")
public class IdentitySessionController {
    public static final String PATH = "/api/v1/identity/session/reconcile";

    private static final Logger LOG = LoggerFactory.getLogger(IdentitySessionController.class);

    private final MemberInvitationService invitations;

    public IdentitySessionController(MemberInvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping(
            path = PATH,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "reconcileIdentitySession",
            summary = "Reconcile authenticated invitation access",
            description =
                    "Idempotently applies one verified pending invitation intent to the current native organization member. An access_updated result requires exactly one new OIDC Authorization Code flow with PKCE before product-domain bootstrap.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Closed, support-safe reconciliation result.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IdentitySessionReconcileResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid bearer token.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "The verified identity is outside the configured organization or membership.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "The pending provisioning intent is ambiguous.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "502",
                description = "The configured identity provider could not be reconciled.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<IdentitySessionReconcileResponse> reconcile(
            @AuthenticationPrincipal Jwt jwt) {
        long started = System.nanoTime();
        String outcome = "failed";
        LOG.info("WEAVE_IDENTITY_SESSION_DIAGNOSTIC operation=reconcile phase=started supportSafe=true");
        try {
            IdentitySessionReconcileResponse response = invitations.reconcileAuthenticated(jwt)
                    ? IdentitySessionReconcileResponse.accessUpdated()
                    : IdentitySessionReconcileResponse.unchanged();
            outcome = "completed";
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(response);
        } finally {
            long elapsedMillis = Math.min(999999L, Math.max(0L, (System.nanoTime() - started) / 1000000L));
            LOG.info("WEAVE_IDENTITY_SESSION_DIAGNOSTIC operation=reconcile phase=ended outcome={} elapsedMillis={} supportSafe=true",
                    outcome, elapsedMillis);
        }
    }
}
