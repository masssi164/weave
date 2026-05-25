package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.chat.ChatConversationsResponse;
import com.massimotter.weave.backend.model.chat.ChatMessageResponse;
import com.massimotter.weave.backend.model.chat.ChatMessagesResponse;
import com.massimotter.weave.backend.model.chat.ChatProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.model.chat.ChatProviderReplacementDryRunResponse;
import com.massimotter.weave.backend.model.chat.ChatSendMessageRequest;
import com.massimotter.weave.backend.service.ChatFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Chat domain", description = "Canonical Weave Chat facade with provider-neutral conversations/messages, Context/Space authorization, capability checks, audit, and support-safe provider replacement seams.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing workspace scope, Context/Space access, or required chat capability.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Chat domain facade is disabled, degraded, or not ready.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ChatController {

    private final ChatFacadeService chatFacadeService;
    private final ChatDomainFacadeService chatDomainFacadeService;

    public ChatController(ChatFacadeService chatFacadeService, ChatDomainFacadeService chatDomainFacadeService) {
        this.chatFacadeService = chatFacadeService;
        this.chatDomainFacadeService = chatDomainFacadeService;
    }

    @GetMapping({"/api/chat/readiness", "/api/v1/chat/readiness"})
    @Operation(summary = "Read member-safe Weave Chat readiness")
    @ApiResponse(responseCode = "200", description = "Member-safe Chat readiness.",
            content = @Content(schema = @Schema(implementation = ChatReadiness.class)))
    public ChatReadiness readiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.memberReadiness(jwt);
    }

    @GetMapping("/api/chat/conversations")
    @Operation(summary = "List canonical Weave Chat conversations")
    @ApiResponse(responseCode = "200", description = "Provider-neutral Chat conversations.",
            content = @Content(schema = @Schema(implementation = ChatConversationsResponse.class)))
    public ChatConversationsResponse conversations(@AuthenticationPrincipal Jwt jwt) {
        return chatFacadeService.conversations(jwt);
    }

    @GetMapping("/api/chat/conversations/{conversationId}/messages")
    @Operation(summary = "List canonical Weave Chat messages")
    @ApiResponse(responseCode = "200", description = "Provider-neutral Chat messages.",
            content = @Content(schema = @Schema(implementation = ChatMessagesResponse.class)))
    public ChatMessagesResponse messages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId) {
        return chatFacadeService.messages(jwt, conversationId);
    }

    @PostMapping("/api/chat/conversations/{conversationId}/messages")
    @Operation(summary = "Send a canonical Weave Chat message as an explicit audited user action")
    @ApiResponse(responseCode = "200", description = "Created provider-neutral Chat message.",
            content = @Content(schema = @Schema(implementation = ChatMessageResponse.class)))
    public ChatMessageResponse sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId,
            @Valid @RequestBody ChatSendMessageRequest request) {
        return chatFacadeService.sendMessage(jwt, conversationId, request);
    }

    @GetMapping({"/api/admin/chat/readiness", "/api/v1/admin/chat/readiness"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Get admin Chat readiness", description = "Returns support-safe Chat provider mapping and readiness diagnostics for admins/operators.")
    public ChatReadiness adminReadiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.adminReadiness(jwt);
    }

    @PostMapping({"/api/admin/chat/migration-preflights", "/api/v1/admin/chat/migration-preflights"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Dry-run a future Chat provider replacement", description = "Creates a support-safe, audited dry-run report for Chat provider replacement. Destructive apply is intentionally unavailable in this contract.")
    public ChatMigrationPreflightReport migrationPreflight(
            @RequestBody(required = false) ChatMigrationPreflightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.preflight(request, jwt);
    }

    @PostMapping("/api/admin/chat/provider-replacements/dry-run")
    @Operation(summary = "Dry-run a Chat provider replacement with support-safe migration evidence")
    @ApiResponse(responseCode = "200", description = "Support-safe provider replacement dry-run report.",
            content = @Content(schema = @Schema(implementation = ChatProviderReplacementDryRunResponse.class)))
    public ChatProviderReplacementDryRunResponse dryRunProviderReplacement(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatProviderReplacementDryRunRequest request) {
        return chatFacadeService.dryRunProviderReplacement(jwt, request);
    }
}
