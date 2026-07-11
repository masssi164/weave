package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import com.massimotter.weave.backend.model.identity.MemberInvitationResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organizations/{organizationId}/member-invitations")
@PreAuthorize("hasAuthority('SCOPE_weave:workspace') and (hasRole('OWNER') or hasRole('ADMIN'))")
public class MemberInvitationController {
    private final MemberInvitationService service;

    public MemberInvitationController(MemberInvitationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberInvitationResponse create(@PathVariable String organizationId,
            @Valid @RequestBody MemberInvitationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        return MemberInvitationResponse.from(service.create(organizationId, request, idempotencyKey, jwt));
    }

    @GetMapping
    public List<MemberInvitationResponse> list(@PathVariable String organizationId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, jwt).stream().map(MemberInvitationResponse::from).toList();
    }

    @PostMapping("/{invitationId}/resend")
    public MemberInvitationResponse resend(@PathVariable String organizationId, @PathVariable UUID invitationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        return MemberInvitationResponse.from(service.resend(organizationId, invitationId, idempotencyKey, jwt));
    }

    @PostMapping("/{invitationId}/revoke")
    public MemberInvitationResponse revoke(@PathVariable String organizationId, @PathVariable UUID invitationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        return MemberInvitationResponse.from(service.revoke(organizationId, invitationId, idempotencyKey, jwt));
    }
}
