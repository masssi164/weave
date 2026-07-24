package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.office.OfficeCapabilitiesResponse;
import com.massimotter.weave.backend.model.office.OfficeLaunchRequest;
import com.massimotter.weave.backend.model.office.OfficeLaunchResponse;
import com.massimotter.weave.backend.service.OfficeFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Office facade", description = "Provider-neutral Office document collaboration facade.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or document capability policy denies Office access.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class OfficeController {

    private final OfficeFacadeService officeFacadeService;

    public OfficeController(OfficeFacadeService officeFacadeService) {
        this.officeFacadeService = officeFacadeService;
    }

    @GetMapping("/api/office/capabilities")
    @Operation(operationId = "getOfficeCapabilities", summary = "Read Office provider-neutral capabilities")
    @ApiResponse(responseCode = "200", description = "Secret-free Office capability metadata.",
            content = @Content(schema = @Schema(implementation = OfficeCapabilitiesResponse.class)))
    public OfficeCapabilitiesResponse capabilities(@AuthenticationPrincipal Jwt jwt) {
        return officeFacadeService.capabilities(jwt);
    }

    @PostMapping("/api/office/launch")
    @Operation(summary = "Launch a future Office document session")
    @ApiResponse(responseCode = "200", description = "Office document launch session.",
            content = @Content(schema = @Schema(implementation = OfficeLaunchResponse.class)))
    @ApiResponse(responseCode = "503", description = "Office provider is not configured or unavailable.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OfficeLaunchResponse launch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OfficeLaunchRequest request) {
        return officeFacadeService.launch(jwt, request);
    }
}
