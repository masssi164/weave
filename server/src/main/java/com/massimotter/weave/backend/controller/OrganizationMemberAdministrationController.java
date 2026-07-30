package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.identity.MemberLifecycleOperationResponse;
import com.massimotter.weave.backend.model.identity.MemberOffboardingRequest;
import com.massimotter.weave.backend.model.identity.OrganizationMemberPageResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberUpdateRequest;
import com.massimotter.weave.backend.model.identity.WeaverEntitlementUpdateRequest;
import com.massimotter.weave.backend.service.OrganizationMemberAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organizations/{organizationId}/members")
@PreAuthorize(
    "hasAuthority('SCOPE_weave:workspace') and (hasRole('OWNER') or hasRole('ADMIN'))")
@Tag(
    name = "Organization members",
    description = "Keycloak-backed member lifecycle through opaque Weave references.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
  @ApiResponse(
      responseCode = "401",
      description = "Missing or invalid bearer token.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "403",
      description = "The caller cannot administer the configured organization.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class OrganizationMemberAdministrationController {
  private final OrganizationMemberAdministrationService service;

  public OrganizationMemberAdministrationController(
      OrganizationMemberAdministrationService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(operationId = "listOrganizationMembers")
  public OrganizationMemberPageResponse list(
      @PathVariable String organizationId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal Jwt jwt) {
    return service.list(organizationId, cursor, size, jwt);
  }

  @GetMapping("/{memberHandle}")
  @Operation(operationId = "getOrganizationMember")
  public OrganizationMemberResponse get(
      @PathVariable String organizationId,
      @PathVariable String memberHandle,
      @AuthenticationPrincipal Jwt jwt) {
    return service.get(organizationId, memberHandle, jwt);
  }

  @PatchMapping("/{memberHandle}")
  @Operation(operationId = "updateOrganizationMemberAccess")
  public OrganizationMemberResponse update(
      @PathVariable String organizationId,
      @PathVariable String memberHandle,
      @Valid @RequestBody OrganizationMemberUpdateRequest request,
      @RequestHeader("If-Match") String expectedVersion,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    return service.update(
        organizationId, memberHandle, request, expectedVersion, idempotencyKey, jwt);
  }

  @PutMapping("/{memberHandle}/capabilities/weaver")
  @Operation(operationId = "updateOrganizationMemberWeaverEntitlement")
  public OrganizationMemberResponse updateWeaverEntitlement(
      @PathVariable String organizationId,
      @PathVariable String memberHandle,
      @Valid @RequestBody WeaverEntitlementUpdateRequest request,
      @RequestHeader("If-Match") String expectedVersion,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    return service.updateWeaverEntitlement(
        organizationId, memberHandle, request, expectedVersion, idempotencyKey, jwt);
  }

  @PostMapping("/{memberHandle}/session-revocations")
  @Operation(operationId = "revokeOrganizationMemberSessions")
  public MemberLifecycleOperationResponse revokeSessions(
      @PathVariable String organizationId,
      @PathVariable String memberHandle,
      @RequestHeader("If-Match") String expectedVersion,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    return service.revokeSessions(
        organizationId, memberHandle, expectedVersion, idempotencyKey, jwt);
  }

  @PostMapping("/{memberHandle}/offboarding")
  @Operation(operationId = "offboardOrganizationMember")
  public MemberLifecycleOperationResponse offboard(
      @PathVariable String organizationId,
      @PathVariable String memberHandle,
      @Valid @RequestBody MemberOffboardingRequest request,
      @RequestHeader("If-Match") String expectedVersion,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    return service.offboard(
        organizationId, memberHandle, expectedVersion, idempotencyKey, jwt);
  }
}
