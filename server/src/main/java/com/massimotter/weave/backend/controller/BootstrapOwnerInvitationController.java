package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.bootstrap.BootstrapOwnerCredential;
import com.massimotter.weave.backend.model.identity.BootstrapOwnerInvitationRequest;
import com.massimotter.weave.backend.model.identity.MemberInvitationResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
    name = "weave.identity.invitations.bootstrap-owner.enabled",
    havingValue = "true")
@Tag(name = "identity-bootstrap")
public class BootstrapOwnerInvitationController {
  public static final String PATH = "/api/bootstrap/owner-invitation";
  public static final String CREDENTIAL_HEADER = "X-Weave-Bootstrap-Token";

  private final BootstrapOwnerCredential credential;
  private final MemberInvitationService invitations;

  public BootstrapOwnerInvitationController(
      BootstrapOwnerCredential credential, MemberInvitationService invitations) {
    this.credential = credential;
    this.invitations = invitations;
  }

  @PostMapping(PATH)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "bootstrapOwnerInvitation",
      summary = "Create or return the first owner invitation in an empty human realm",
      security = @SecurityRequirement(name = "owner-bootstrap-token"))
  public MemberInvitationResponse create(
      @RequestHeader(CREDENTIAL_HEADER) String suppliedCredential,
      @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
      @Valid @RequestBody BootstrapOwnerInvitationRequest request) {
    try {
      if (!credential.matches(suppliedCredential)) {
        throw unauthorized();
      }
    } catch (IllegalStateException unavailable) {
      throw unavailable();
    }
    MemberInvitationResponse invitation = invitations.bootstrapOwner(request, idempotencyKey);
    try {
      credential.consumeAfterSuccess();
    } catch (IllegalStateException unavailable) {
      throw unavailable();
    }
    return invitation;
  }

  private static ApiErrorException unavailable() {
    return new ApiErrorException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "owner-bootstrap-unavailable",
        "The protected owner bootstrap operation is unavailable.",
        Map.of());
  }

  private static ApiErrorException unauthorized() {
    return new ApiErrorException(
        HttpStatus.UNAUTHORIZED,
        "owner-bootstrap-unauthorized",
        "The protected owner bootstrap credential is invalid.",
        Map.of());
  }
}
