package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderSetupResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialListResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialRequest;
import com.massimotter.weave.backend.model.files.FileSetupCredentialResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Files", description = "Authenticated Files control plane. File data-plane operations use the Weave WebDAV facade at /dav/files.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class FilesController {

    private final FilesFacadeService filesFacadeService;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public FilesController(FilesFacadeService filesFacadeService, WorkspaceCapabilityService workspaceCapabilityService) {
        this.filesFacadeService = filesFacadeService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/files/readiness")
    @Operation(
            operationId = "getFilesReadiness",
            summary = "Get Files readiness",
            description = "Returns the member-safe, provider-neutral Files capability readiness derived from the canonical workspace capability snapshot.")
    @ApiResponse(responseCode = "200", description = "Files capability readiness.",
            content = @Content(schema = @Schema(implementation = WorkspaceCapabilityStatusResponse.class)))
    public WorkspaceCapabilityStatusResponse getFilesReadiness(@AuthenticationPrincipal Jwt jwt) {
        return workspaceCapabilityService.snapshot(jwt).files();
    }

    @GetMapping("/api/files/native-provider-setup")
    @Operation(
            operationId = "getFilesNativeProviderSetup",
            summary = "Describe native Files provider setup",
            description = "Returns support-safe iOS File Provider and Android DocumentsProvider setup metadata backed only by Weave-owned file facade endpoints.")
    @ApiResponse(responseCode = "200", description = "Native Files provider setup metadata.",
            content = @Content(schema = @Schema(implementation = FileNativeProviderSetupResponse.class)))
    public FileNativeProviderSetupResponse getFilesNativeProviderSetup(@AuthenticationPrincipal Jwt jwt) {
        return filesFacadeService.nativeProviderSetup(jwt);
    }

    @GetMapping("/api/files/client-setup/credentials")
    @Operation(
            operationId = "getFilesSetupCredentials",
            summary = "List revocable Files WebDAV setup credential references")
    @ApiResponse(responseCode = "200", description = "Files setup credentials without secret material.",
            content = @Content(schema = @Schema(implementation = FileSetupCredentialListResponse.class)))
    public FileSetupCredentialListResponse setupCredentials() {
        return filesFacadeService.setupCredentials();
    }

    @PostMapping("/api/files/client-setup/credentials")
    @Operation(
            operationId = "createFilesSetupCredential",
            summary = "Create a revocable Files WebDAV setup credential and return its secret once")
    @ApiResponse(responseCode = "200", description = "New Files credential with one-time secret material.",
            content = @Content(schema = @Schema(implementation = FileSetupCredentialResponse.class)))
    public FileSetupCredentialResponse createSetupCredential(
            @Valid @RequestBody FileSetupCredentialRequest request) {
        return filesFacadeService.createSetupCredential(request);
    }

    @DeleteMapping("/api/files/client-setup/credentials/{credentialId}")
    @Operation(
            operationId = "revokeFilesSetupCredential",
            summary = "Revoke a Files WebDAV setup credential reference")
    @ApiResponse(responseCode = "200", description = "Revoked Files credential without secret material.",
            content = @Content(schema = @Schema(implementation = FileSetupCredentialResponse.class)))
    public FileSetupCredentialResponse revokeSetupCredential(@PathVariable @Size(max = 128) String credentialId) {
        return filesFacadeService.revokeSetupCredential(credentialId);
    }
}
