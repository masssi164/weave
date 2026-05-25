package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.model.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat", description = "Provider-neutral Weave Chat domain facade.")
public class ChatController {

    private final ChatDomainFacadeService chatDomainFacadeService;

    public ChatController(ChatDomainFacadeService chatDomainFacadeService) {
        this.chatDomainFacadeService = chatDomainFacadeService;
    }

    @GetMapping({"/api/chat/readiness", "/api/v1/chat/readiness"})
    @Operation(
            summary = "Get member-safe Chat readiness",
            description = "Returns stable Weave Chat readiness for member clients. Provider mappings, diagnostics, credentials, raw URLs, and raw downstream errors remain admin/operator side.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member-safe Chat readiness.",
                    content = @Content(schema = @Schema(implementation = ChatReadiness.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChatReadiness readiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.memberReadiness(jwt);
    }

    @GetMapping({"/api/chat/conversations", "/api/v1/chat/conversations"})
    @Operation(
            summary = "List Weave Chat conversations",
            description = "Returns canonical Weave conversations only when the server-owned Chat mapping is ready; otherwise fails closed with stable member state and no provider payload.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    public ChatConversations conversations(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.conversations(jwt);
    }

    @GetMapping({"/api/chat/conversations/{conversationId}/messages", "/api/v1/chat/conversations/{conversationId}/messages"})
    @Operation(
            summary = "List Weave Chat messages",
            description = "Returns canonical Weave messages only through the server-owned Chat facade.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    public ChatMessages messages(@PathVariable String conversationId, @AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.messages(conversationId, jwt);
    }

    @GetMapping({"/api/admin/chat/readiness", "/api/v1/admin/chat/readiness"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(
            summary = "Get admin Chat readiness",
            description = "Returns support-safe Chat provider mapping and readiness diagnostics for admins/operators.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    public ChatReadiness adminReadiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.adminReadiness(jwt);
    }

    @PostMapping({"/api/admin/chat/migration-preflights", "/api/v1/admin/chat/migration-preflights"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(
            summary = "Dry-run a future Chat provider replacement",
            description = "Creates a support-safe, audited dry-run report for Chat provider replacement. Destructive apply is intentionally unavailable in this contract.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    public ChatMigrationPreflightReport migrationPreflight(
            @RequestBody(required = false) ChatMigrationPreflightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.preflight(request, jwt);
    }
}
