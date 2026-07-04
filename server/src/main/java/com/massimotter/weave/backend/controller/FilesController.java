package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderSetupResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@Tag(name = "Files", description = "Authenticated product files facade backed by Nextcloud APIs.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Downstream files adapter is not configured or unavailable.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class FilesController {

    private final FilesFacadeService filesFacadeService;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public FilesController(FilesFacadeService filesFacadeService, WorkspaceCapabilityService workspaceCapabilityService) {
        this.filesFacadeService = filesFacadeService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/files")
    @Operation(
            operationId = "listFiles",
            summary = "List files and folders",
            description = "Lists files through the Weave-owned product files facade without exposing backing provider URLs or credentials.")
    @ApiResponse(responseCode = "200", description = "Folder listing.",
            content = @Content(schema = @Schema(implementation = FileListResponse.class)))
    public FileListResponse listFiles(
            @RequestParam(defaultValue = "/")
            @Size(max = 1024)
            @Pattern(regexp = "/.*", message = "must start with /")
            String path) {
        return filesFacadeService.list(path);
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

    @PostMapping("/api/files/folders")
    @Operation(
            operationId = "createFilesFolder",
            summary = "Create a folder",
            description = "Creates a folder through the Weave-owned product files facade.")
    @ApiResponse(responseCode = "200", description = "Created folder metadata.",
            content = @Content(schema = @Schema(implementation = FileItemResponse.class)))
    public FileItemResponse createFilesFolder(@Valid @RequestBody CreateFolderRequest request) {
        return filesFacadeService.createFolder(request);
    }

    @PostMapping(value = "/api/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "uploadFile",
            summary = "Upload a file",
            description = "Uploads a file through the Weave-owned product files facade.")
    @ApiResponse(responseCode = "200", description = "Uploaded file metadata.",
            content = @Content(schema = @Schema(implementation = FileUploadResponse.class)))
    public FileUploadResponse uploadFile(
            @RequestParam(defaultValue = "/")
            @Size(max = 1024)
            @Pattern(regexp = "/.*", message = "must start with /")
            String parentPath,
            @RequestPart("file") MultipartFile file) {
        return filesFacadeService.upload(parentPath, file);
    }

    @GetMapping("/api/files/{id}/download")
    @Operation(
            operationId = "downloadFile",
            summary = "Download a file",
            description = "Downloads file bytes through the Weave-owned product files facade without exposing backing provider URLs or credentials.")
    @ApiResponse(responseCode = "200", description = "Downloaded file bytes.",
            content = @Content(mediaType = MediaType.ALL_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> downloadFile(@PathVariable @Size(max = 2048) String id) {
        DownloadedFile file = filesFacadeService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }

    @DeleteMapping("/api/files/{id}")
    @Operation(
            operationId = "deleteFile",
            summary = "Delete a file or folder",
            description = "Deletes a file or folder through the Weave-owned product files facade.")
    @ApiResponse(responseCode = "204", description = "File or folder deleted.")
    public ResponseEntity<Void> deleteFile(@PathVariable @Size(max = 2048) String id) {
        filesFacadeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
