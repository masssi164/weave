package com.massimotter.weave.backend.chat.api;

import com.massimotter.weave.backend.chat.application.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import com.massimotter.weave.backend.chat.application.ChatProviderReplacementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Chat domain", description = "Canonical Weave Chat facade with provider-neutral conversations/messages, Context/Space authorization, capability checks, audit, and support-safe provider replacement seams.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing workspace scope, Context/Space access, or required chat capability.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Chat domain facade is disabled, degraded, or not ready.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ChatController {

    private final ChatProviderReplacementService chatFacadeService;
    private final ChatDomainFacadeService chatDomainFacadeService;

    public ChatController(ChatProviderReplacementService chatFacadeService, ChatDomainFacadeService chatDomainFacadeService) {
        this.chatFacadeService = chatFacadeService;
        this.chatDomainFacadeService = chatDomainFacadeService;
    }

    @GetMapping("/api/chat/readiness")
    @Operation(operationId = "getChatReadiness", summary = "Read member-safe Weave Chat readiness")
    @ApiResponse(responseCode = "200", description = "Member-safe Chat readiness.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatReadiness.class)))
    public ChatReadiness readiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.memberReadiness(jwt);
    }

    @GetMapping("/api/admin/chat/readiness")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(operationId = "getAdminChatReadiness", summary = "Get admin Chat readiness", description = "Returns support-safe Chat provider mapping and readiness diagnostics for admins/operators.")
    @ApiResponse(responseCode = "200", description = "Support-safe Admin Chat readiness.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatReadiness.class)))
    public ChatReadiness adminReadiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.adminReadiness(jwt);
    }

    @PostMapping("/api/admin/chat/migration-preflights")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(operationId = "createChatMigrationPreflight", summary = "Dry-run a future Chat provider replacement", description = "Creates a support-safe, audited dry-run report for Chat provider replacement. Destructive apply is intentionally unavailable in this contract.")
    @ApiResponse(responseCode = "200", description = "Support-safe Chat migration preflight report.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatMigrationPreflightReport.class)))
    public ChatMigrationPreflightReport migrationPreflight(
            @RequestBody(required = false) ChatMigrationPreflightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.preflight(request, jwt);
    }

    @PostMapping("/api/admin/chat/provider-replacements/dry-run")
    @Operation(operationId = "dryRunChatProviderReplacement", summary = "Dry-run a Chat provider replacement with support-safe migration evidence")
    @ApiResponse(responseCode = "200", description = "Support-safe provider replacement dry-run report.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatProviderReplacementDryRunResponse.class)))
    public ChatProviderReplacementDryRunResponse dryRunProviderReplacement(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatProviderReplacementDryRunRequest request) {
        return chatFacadeService.dryRunProviderReplacement(jwt, request);
    }
}
