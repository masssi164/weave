package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.chat.ChatProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.model.chat.ChatProviderReplacementDryRunResponse;
import com.massimotter.weave.backend.model.chat.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerRecordResponse;
import com.massimotter.weave.backend.model.chat.DecisionLedgerRecordsResponse;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleCreateRequest;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleResponse;
import com.massimotter.weave.backend.model.chat.MeetingCapsulesResponse;
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
    @Operation(operationId = "getChatReadiness", summary = "Read member-safe Weave Chat readiness")
    @ApiResponse(responseCode = "200", description = "Member-safe Chat readiness.",
            content = @Content(schema = @Schema(implementation = ChatReadiness.class)))
    public ChatReadiness readiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.memberReadiness(jwt);
    }

    @GetMapping({"/api/chat/conversations/{conversationId}/decisions", "/api/v1/chat/conversations/{conversationId}/decisions"})
    @Operation(summary = "Read channel Decision Ledger records")
    @ApiResponse(responseCode = "200", description = "Source-linked Decision Ledger records.",
            content = @Content(schema = @Schema(implementation = DecisionLedgerRecordsResponse.class)))
    public DecisionLedgerRecordsResponse decisions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId) {
        return chatFacadeService.decisions(jwt, conversationId);
    }

    @PostMapping({"/api/chat/conversations/{conversationId}/decisions", "/api/v1/chat/conversations/{conversationId}/decisions"})
    @Operation(summary = "Create a channel Decision Ledger record")
    @ApiResponse(responseCode = "200", description = "Created source-linked Decision Ledger record.",
            content = @Content(schema = @Schema(implementation = DecisionLedgerRecordResponse.class)))
    public DecisionLedgerRecordResponse createDecision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId,
            @Valid @RequestBody DecisionLedgerCreateRequest request) {
        return chatFacadeService.createDecision(jwt, conversationId, request);
    }

    @GetMapping({"/api/chat/conversations/{conversationId}/meeting-capsules", "/api/v1/chat/conversations/{conversationId}/meeting-capsules"})
    @Operation(summary = "Read channel Meeting Capsules")
    @ApiResponse(responseCode = "200", description = "Durable channel Meeting Capsules with fail-closed media controls.",
            content = @Content(schema = @Schema(implementation = MeetingCapsulesResponse.class)))
    public MeetingCapsulesResponse meetingCapsules(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId) {
        return chatFacadeService.meetingCapsules(jwt, conversationId);
    }

    @PostMapping({"/api/chat/conversations/{conversationId}/meeting-capsules", "/api/v1/chat/conversations/{conversationId}/meeting-capsules"})
    @Operation(summary = "Create a channel Meeting Capsule")
    @ApiResponse(responseCode = "200", description = "Created Meeting Capsule.",
            content = @Content(schema = @Schema(implementation = MeetingCapsuleResponse.class)))
    public MeetingCapsuleResponse createMeetingCapsule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId,
            @Valid @RequestBody MeetingCapsuleCreateRequest request) {
        return chatFacadeService.createMeetingCapsule(jwt, conversationId, request);
    }

    @GetMapping({"/api/admin/chat/readiness", "/api/v1/admin/chat/readiness"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(operationId = "getAdminChatReadiness", summary = "Get admin Chat readiness", description = "Returns support-safe Chat provider mapping and readiness diagnostics for admins/operators.")
    public ChatReadiness adminReadiness(@AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.adminReadiness(jwt);
    }

    @PostMapping({"/api/admin/chat/migration-preflights", "/api/v1/admin/chat/migration-preflights"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(operationId = "createChatMigrationPreflight", summary = "Dry-run a future Chat provider replacement", description = "Creates a support-safe, audited dry-run report for Chat provider replacement. Destructive apply is intentionally unavailable in this contract.")
    public ChatMigrationPreflightReport migrationPreflight(
            @RequestBody(required = false) ChatMigrationPreflightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chatDomainFacadeService.preflight(request, jwt);
    }

    @PostMapping("/api/admin/chat/provider-replacements/dry-run")
    @Operation(operationId = "dryRunChatProviderReplacement", summary = "Dry-run a Chat provider replacement with support-safe migration evidence")
    @ApiResponse(responseCode = "200", description = "Support-safe provider replacement dry-run report.",
            content = @Content(schema = @Schema(implementation = ChatProviderReplacementDryRunResponse.class)))
    public ChatProviderReplacementDryRunResponse dryRunProviderReplacement(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatProviderReplacementDryRunRequest request) {
        return chatFacadeService.dryRunProviderReplacement(jwt, request);
    }
}
