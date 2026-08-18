package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import com.massimotter.weave.backend.model.identity.MemberInvitationResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/organizations/{organizationId}/invitations")
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
        return service.create(organizationId, request, idempotencyKey, jwt);
    }

    @GetMapping
    public List<MemberInvitationResponse> list(@PathVariable String organizationId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, jwt);
    }

    @PostMapping("/{invitationHandle}/resend")
    public MemberInvitationResponse resend(@PathVariable String organizationId, @PathVariable String invitationHandle,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        return service.resend(organizationId, invitationHandle, idempotencyKey, jwt);
    }

    @DeleteMapping("/{invitationHandle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String organizationId, @PathVariable String invitationHandle,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        service.revoke(organizationId, invitationHandle, idempotencyKey, jwt);
    }
}
