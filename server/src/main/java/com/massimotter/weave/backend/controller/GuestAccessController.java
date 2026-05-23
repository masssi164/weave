package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.guest.GuestAccessContractResponse;
import com.massimotter.weave.backend.model.guest.GuestInvitationRequest;
import com.massimotter.weave.backend.model.guest.GuestInvitationResponse;
import com.massimotter.weave.backend.service.guest.GuestAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Guest Access", description = "Guest identity and explicit access policy contracts.")
@SecurityRequirement(name = "bearer-jwt")
public class GuestAccessController {

    private final GuestAccessService guestAccessService;

    public GuestAccessController(GuestAccessService guestAccessService) {
        this.guestAccessService = guestAccessService;
    }

    @GetMapping("/api/guest/access-contract")
    @Operation(summary = "Get guest identity and policy contract")
    public GuestAccessContractResponse contract() {
        return guestAccessService.contract();
    }

    @PostMapping("/api/guest/invitations")
    @Operation(summary = "Create a guest invitation when guest access is enabled")
    @ApiResponse(responseCode = "503", description = "Guest access is disabled by default.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public GuestInvitationResponse invite(@Valid @RequestBody GuestInvitationRequest request) {
        return guestAccessService.invite(request);
    }
}
