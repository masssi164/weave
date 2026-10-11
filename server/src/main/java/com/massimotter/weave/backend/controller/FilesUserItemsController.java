package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.files.FilesUserItemResponse;
import com.massimotter.weave.backend.model.files.FilesUserListResponse;
import com.massimotter.weave.backend.model.files.FilesUserCreateFolderRequest;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Generated User HTTP projection of Weave-owned Files identities. */
@RestController
@Validated
@Tag(name = "Files User", description = "Member Files operations and read-only exchanged MCP workload reads. Provider-visible objects without a Weave resource grant are hidden.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Authentication required.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Member, workload or Space access denied.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource absent or not visible.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "423", description = "Resource locked at the active provider.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "507", description = "Insufficient provider storage.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Provider or identity mapping unavailable.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class FilesUserItemsController {
    private final FilesUserApiService files;

    public FilesUserItemsController(FilesUserApiService files) { this.files = files; }

    @GetMapping(value = "/api/files/items", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listFilesItems", summary = "List Weave-authorized Files children")
    @ApiResponse(responseCode = "200", description = "Visible children.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FilesUserListResponse.class)))
    public FilesUserListResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String parentId) {
        return files.list(jwt, parentId);
    }

    @GetMapping(value = "/api/files/items/{fileId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getFilesItem", summary = "Inspect a Weave Files item")
    @ApiResponse(responseCode = "200", description = "Visible item metadata.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FilesUserItemResponse.class)))
    public FilesUserItemResponse inspect(@AuthenticationPrincipal Jwt jwt, @PathVariable String fileId) {
        return files.inspect(jwt, fileId);
    }

    @PostMapping(value = "/api/files/items/folders", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createFilesFolder", summary = "Create an empty Files folder at an absent name",
            description = "Requires createFolder in the parent's current allowedActions. The current release "
                    + "supports atomic root-folder creation; other parents fail closed until their identity "
                    + "can be bound atomically by the selected provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Created folder, or the same completed idempotent result.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FilesUserItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid folder name, parent or idempotency key.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name already exists or idempotency key conflicts.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "412", description = "Atomic absent-name precondition failed.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported request media type; use application/json.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "428", description = "If-None-Match: * required.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public FilesUserItemResponse createFolder(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FilesUserCreateFolderRequest request,
            @Parameter(required = true, description = "Must be * for atomic absent-name creation.")
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @Parameter(required = true, description = "16 to 128 character key for durable User HTTP intent.",
                    schema = @Schema(minLength = 16, maxLength = 128))
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return files.createFolder(jwt, request.parentFileId(), request.name(), ifNoneMatch, idempotencyKey);
    }

    @PostMapping(value = "/api/files/items/uploads", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "uploadFilesItemContent", summary = "Upload bounded binary content at an absent name",
            description = "Accepts at most 26214400 bytes (25 MiB), including an empty file. Requires upload "
                    + "in the parent's current allowedActions. The current release supports atomic creation "
                    + "in file:root; unsupported parent identity guarantees fail closed.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Created file, or the same completed idempotent result.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FilesUserItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid upload parameters or idempotency key.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name or idempotency key conflict.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "412", description = "Atomic absent-name precondition failed.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Upload exceeds the byte limit.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported request media type; use application/octet-stream.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "428", description = "If-None-Match: * required.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public FilesUserItemResponse upload(@AuthenticationPrincipal Jwt jwt,
            @RequestParam String parentId,
            @Parameter(schema = @Schema(minLength = 1, maxLength = 255)) @RequestParam String name,
            @Parameter(schema = @Schema(maxLength = 255))
            @RequestParam(required = false) String mediaType,
            @Parameter(required = true, description = "Must be * for atomic absent-name creation.")
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @Parameter(required = true, description = "16 to 128 character durable operation key.",
                    schema = @Schema(minLength = 16, maxLength = 128))
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) throws IOException {
        return files.upload(jwt, parentId, name, mediaType, boundedBody(request), ifNoneMatch, idempotencyKey);
    }

    @PutMapping(value = "/api/files/items/{fileId}/content", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateFilesItemContent", summary = "Replace bounded binary content against a strong validator",
            description = "Accepts at most 26214400 bytes (25 MiB). Requires updateContent in the item's "
                    + "current allowedActions and a provider that atomically enforces both identity and version. "
                    + "Unsupported providers fail closed; a content ETag is distinct from the item revision.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated file metadata.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FilesUserItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid content parameters or idempotency key.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency or provider conflict.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "412", description = "Weak or stale content validator.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Current or replacement content exceeds the byte limit.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported request media type; use application/octet-stream.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "428", description = "Strong If-Match required.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public FilesUserItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String fileId,
            @Parameter(schema = @Schema(maxLength = 255))
            @RequestParam(required = false) String mediaType,
            @Parameter(required = true, description = "Strong ETag returned by the last content download.")
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Parameter(required = true, description = "16 to 128 character durable operation key.",
                    schema = @Schema(minLength = 16, maxLength = 128))
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) throws IOException {
        return files.update(jwt, fileId, mediaType, boundedBody(request), ifMatch, idempotencyKey);
    }

    private byte[] boundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > FilesUserApiService.MAX_UPLOAD_BYTES) {
            throw uploadTooLarge();
        }
        byte[] bytes = request.getInputStream().readNBytes(FilesUserApiService.MAX_UPLOAD_BYTES + 1);
        if (bytes.length > FilesUserApiService.MAX_UPLOAD_BYTES) {
            throw uploadTooLarge();
        }
        return bytes;
    }

    private ApiErrorException uploadTooLarge() {
        return new ApiErrorException(HttpStatus.PAYLOAD_TOO_LARGE, "files-upload-too-large",
                "Upload exceeds the byte limit.", Map.of("module", "files"));
    }

    @GetMapping("/api/files/items/{fileId}/content")
    @Operation(operationId = "downloadFilesItemContent", summary = "Download bounded binary Files content",
            description = "Returns at most 26214400 bytes (25 MiB) from an identity-bound conditional provider "
                    + "read. Requires download in the item's current allowedActions. Larger files fail with 413.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exact content bytes with strong content ETag and SHA-256 digest.",
                    headers = {
                            @Header(name = HttpHeaders.ETAG, description = "Strong SHA-256 content validator.",
                                    schema = @Schema(type = "string")),
                            @Header(name = "Content-Digest", description = "RFC 9530 SHA-256 digest of the returned bytes.",
                                    schema = @Schema(type = "string")),
                            @Header(name = HttpHeaders.CONTENT_LENGTH, description = "Exact returned byte count.",
                                    schema = @Schema(type = "integer", format = "int64")),
                            @Header(name = HttpHeaders.CONTENT_TYPE, description = "Returned binary media type.",
                                    schema = @Schema(type = "string"))
                    },
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "304", description = "Strong content ETag matches If-None-Match.",
                    headers = @Header(name = HttpHeaders.ETAG, description = "Matching strong content validator.",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "412", description = "Provider content changed during conditional download.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds the bounded download limit.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable String fileId,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var content = files.download(jwt, fileId);
        if (matchesIfNoneMatch(ifNoneMatch, content.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(content.etag()).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.mediaType())
                .header("Content-Digest", content.digest())
                .eTag(content.etag())
                .contentLength(content.bytes().length)
                .body(content.bytes());
    }

    private boolean matchesIfNoneMatch(String supplied, String current) {
        return supplied != null && Arrays.stream(supplied.split(","))
                .map(String::trim)
                .anyMatch(candidate -> candidate.equals("*") || candidate.equals(current)
                        || candidate.equals("W/" + current));
    }
}
