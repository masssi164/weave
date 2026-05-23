package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
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

    public FilesController(FilesFacadeService filesFacadeService) {
        this.filesFacadeService = filesFacadeService;
    }

    @GetMapping("/api/files")
    @Operation(summary = "List files and folders")
    @ApiResponse(responseCode = "200", description = "Folder listing.",
            content = @Content(schema = @Schema(implementation = FileListResponse.class)))
    public FileListResponse list(
            @RequestParam(defaultValue = "/")
            @Size(max = 1024)
            @Pattern(regexp = "/.*", message = "must start with /")
            String path) {
        return filesFacadeService.list(path);
    }

    @PostMapping("/api/files/folders")
    @Operation(summary = "Create a folder")
    @ApiResponse(responseCode = "200", description = "Created folder metadata.",
            content = @Content(schema = @Schema(implementation = FileItemResponse.class)))
    public FileItemResponse createFolder(@Valid @RequestBody CreateFolderRequest request) {
        return filesFacadeService.createFolder(request);
    }

    @PostMapping(value = "/api/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file")
    @ApiResponse(responseCode = "200", description = "Uploaded file metadata.",
            content = @Content(schema = @Schema(implementation = FileUploadResponse.class)))
    public FileUploadResponse upload(
            @RequestParam(defaultValue = "/")
            @Size(max = 1024)
            @Pattern(regexp = "/.*", message = "must start with /")
            String parentPath,
            @RequestPart("file") MultipartFile file) {
        return filesFacadeService.upload(parentPath, file);
    }

    @GetMapping("/api/files/{id}/download")
    @Operation(summary = "Download a file")
    public ResponseEntity<byte[]> download(@PathVariable @Size(max = 2048) String id) {
        DownloadedFile file = filesFacadeService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }

    @DeleteMapping("/api/files/{id}")
    @Operation(summary = "Delete a file or folder")
    public ResponseEntity<Void> delete(@PathVariable @Size(max = 2048) String id) {
        filesFacadeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
