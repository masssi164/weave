package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.WebDavPropfindListing;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@Hidden
public class FilesWebDavController {

    private static final String DAV_ROOT = "/dav/files";
    private static final MediaType XML = MediaType.APPLICATION_XML;

    private final FilesFacadeService filesFacadeService;

    public FilesWebDavController(FilesFacadeService filesFacadeService) {
        this.filesFacadeService = filesFacadeService;
    }

    @RequestMapping(value = {"/dav/files", "/dav/files/", "/dav/files/**"}, method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return options();
    }

    @RequestMapping({"/dav/files", "/dav/files/", "/dav/files/**"})
    public ResponseEntity<?> handle(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        try {
            return switch (method) {
                case "OPTIONS" -> options();
                case "PROPFIND" -> propfind(request);
                case "GET" -> get(request, false);
                case "HEAD" -> get(request, true);
                case "PUT" -> put(request);
                case "MKCOL" -> mkcol(request);
                case "DELETE" -> delete(request);
                case "MOVE" -> move(request);
                case "COPY" -> copy(request);
                case "LOCK" -> lock(request);
                case "UNLOCK" -> unlock(request);
                default -> unsupportedMethod(method);
            };
        } catch (ApiErrorException exception) {
            return davError(exception);
        }
    }

    private ResponseEntity<Void> options() {
        return ResponseEntity.noContent()
                .header("DAV", "1")
                .header(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK")
                .header("MS-Author-Via", "DAV")
                .build();
    }

    private ResponseEntity<String> propfind(HttpServletRequest request) {
        String depth = request.getHeader("Depth");
        if (depth != null && !depth.isBlank() && !"0".equals(depth) && !"1".equals(depth)) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "webdav-depth-not-supported",
                    "Only Depth 0 and Depth 1 are supported for Weave Files WebDAV.",
                    Map.of("module", "files", "operation", "webdav-propfind"));
        }

        String path = productPath(request);
        WebDavPropfindListing listing = filesFacadeService.webDavPropfind(path);
        boolean includeChildren = "1".equals(depth);
        return ResponseEntity.status(207)
                .contentType(XML)
                .header("DAV", "1")
                .body(multistatus(listing, includeChildren));
    }

    private ResponseEntity<byte[]> get(HttpServletRequest request, boolean headOnly) {
        String path = productPath(request);
        DownloadedFile file = filesFacadeService.download(path);
        String etag = filesFacadeService.etagFor(path);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.content().length)
                .eTag(etag)
                .cacheControl(CacheControl.empty().noTransform())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename()).build().toString());
        return builder.body(headOnly ? null : file.content());
    }

    private ResponseEntity<Void> put(HttpServletRequest request) {
        String path = productPath(request);
        WebDavMutationResult result = filesFacadeService.putWebDavFile(
                path,
                requestBody(request),
                request.getContentType(),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader(HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), false))
                .build();
    }

    private ResponseEntity<Void> mkcol(HttpServletRequest request) {
        WebDavMutationResult result = filesFacadeService.createWebDavFolder(
                productPath(request),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader(HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), true))
                .build();
    }

    private ResponseEntity<Void> delete(HttpServletRequest request) {
        filesFacadeService.deleteWebDavPath(
                productPath(request),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader("If"));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> copy(HttpServletRequest request) {
        WebDavMutationResult result = filesFacadeService.copyWebDavPath(
                productPath(request),
                destinationPath(request),
                overwrite(request),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader("If"));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.NO_CONTENT)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), "folder".equals(result.item().type())))
                .build();
    }

    private ResponseEntity<Void> move(HttpServletRequest request) {
        WebDavMutationResult result = filesFacadeService.moveWebDavPath(
                productPath(request),
                destinationPath(request),
                overwrite(request),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader("If"));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.NO_CONTENT)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), "folder".equals(result.item().type())))
                .build();
    }

    private ResponseEntity<String> lock(HttpServletRequest request) {
        WebDavLockResult result = filesFacadeService.lockWebDavPath(productPath(request), request.getHeader("If"));
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(XML)
                .header("Lock-Token", "<" + result.token() + ">")
                .header("Timeout", "Second-" + result.timeoutSeconds())
                .body(lockDiscoveryXml(result));
    }

    private ResponseEntity<Void> unlock(HttpServletRequest request) {
        filesFacadeService.unlockWebDavPath(productPath(request), request.getHeader("Lock-Token"));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<String> unsupportedMethod(String method) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .contentType(XML)
                .header(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK")
                .header("X-Weave-Error-Code", "webdav-method-not-implemented")
                .body(errorXml("webdav-method-not-implemented",
                        "Weave Files WebDAV does not implement " + method
                                + " in the current Files protocol slice."));
    }

    private ResponseEntity<String> davError(ApiErrorException exception) {
        HttpStatus status = webdavStatus(exception);
        return ResponseEntity.status(status)
                .contentType(XML)
                .header("X-Weave-Error-Code", exception.code())
                .body(errorXml(exception.code(), exception.getMessage()));
    }

    private HttpStatus webdavStatus(ApiErrorException exception) {
        if ("file-conflict".equals(exception.code())
                && Integer.valueOf(423).equals(exception.details().get("downstreamStatus"))) {
            return HttpStatus.LOCKED;
        }
        return exception.status();
    }

    private String productPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        String suffix = requestPath.length() <= DAV_ROOT.length() ? "" : requestPath.substring(DAV_ROOT.length());
        String decoded = UriUtils.decode(suffix, StandardCharsets.UTF_8);
        String productPath = decoded == null || decoded.isBlank() ? "/" : decoded;
        if (!productPath.startsWith("/")) {
            productPath = "/" + productPath;
        }
        return FilePathCodec.normalizeProductPath(productPath);
    }

    private String destinationPath(HttpServletRequest request) {
        String destination = request.getHeader("Destination");
        if (destination == null || destination.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "webdav-destination-required",
                    "WebDAV COPY and MOVE require a Destination header.",
                    Map.of("module", "files", "operation", "webdav-destination"));
        }
        String rawPath;
        try {
            rawPath = java.net.URI.create(destination).getRawPath();
        } catch (IllegalArgumentException exception) {
            rawPath = destination;
        }
        if (rawPath == null) {
            rawPath = "";
        }
        String decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        if (decoded.startsWith(DAV_ROOT)) {
            decoded = decoded.substring(DAV_ROOT.length());
        } else if (decoded.startsWith("/")) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "webdav-destination-outside-facade",
                    "WebDAV COPY and MOVE destinations must stay under the Weave Files WebDAV facade.",
                    Map.of("module", "files", "operation", "webdav-destination"));
        }
        if (decoded.isBlank()) {
            decoded = "/";
        }
        if (!decoded.startsWith("/")) {
            decoded = "/" + decoded;
        }
        return FilePathCodec.normalizeProductPath(decoded);
    }

    private boolean overwrite(HttpServletRequest request) {
        String overwrite = request.getHeader("Overwrite");
        return overwrite == null || overwrite.isBlank() || !"F".equalsIgnoreCase(overwrite.trim());
    }

    private byte[] requestBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "file-upload-unreadable",
                    "Uploaded file could not be read by the backend.",
                    Map.of("module", "files", "operation", "webdav-put"));
        }
    }

    private String multistatus(WebDavPropfindListing listing, boolean includeChildren) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:">
                """);
        appendFolderResponse(xml, listing.requested());
        if (includeChildren) {
            for (WebDavPropfindResource resource : listing.children()) {
                if ("folder".equals(resource.item().type())) {
                    appendFolderResponse(xml, resource);
                } else {
                    appendFileResponse(xml, resource);
                }
            }
        }
        xml.append("</d:multistatus>");
        return xml.toString();
    }

    private void appendFolderResponse(StringBuilder xml, WebDavPropfindResource resource) {
        FileItemResponse item = resource.item();
        xml.append("  <d:response>\n")
                .append("    <d:href>").append(escapeXml(davHref(item.path(), true))).append("</d:href>\n")
                .append("    <d:propstat>\n")
                .append("      <d:prop>\n")
                .append("        <d:displayname>").append(escapeXml(displayName(item.path()))).append("</d:displayname>\n")
                .append("        <d:resourcetype><d:collection/></d:resourcetype>\n");
        appendEtag(xml, resource.etag());
        appendLockProperties(xml);
        xml.append("      </d:prop>\n")
                .append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
                .append("    </d:propstat>\n")
                .append("  </d:response>\n");
    }

    private void appendFileResponse(StringBuilder xml, WebDavPropfindResource resource) {
        FileItemResponse item = resource.item();
        xml.append("  <d:response>\n")
                .append("    <d:href>").append(escapeXml(davHref(item.path(), false))).append("</d:href>\n")
                .append("    <d:propstat>\n")
                .append("      <d:prop>\n")
                .append("        <d:displayname>").append(escapeXml(item.name())).append("</d:displayname>\n")
                .append("        <d:resourcetype/>\n");
        appendEtag(xml, resource.etag());
        appendLockProperties(xml);
        if (item.mimeType() != null && !item.mimeType().isBlank()) {
            xml.append("        <d:getcontenttype>").append(escapeXml(item.mimeType())).append("</d:getcontenttype>\n");
        }
        if (item.size() != null) {
            xml.append("        <d:getcontentlength>").append(item.size()).append("</d:getcontentlength>\n");
        }
        if (item.modifiedAt() != null) {
            xml.append("        <d:getlastmodified>")
                    .append(escapeXml(httpDate(item.modifiedAt())))
                    .append("</d:getlastmodified>\n");
        }
        xml.append("      </d:prop>\n")
                .append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
                .append("    </d:propstat>\n")
                .append("  </d:response>\n");
    }

    private void appendEtag(StringBuilder xml, String etag) {
        if (etag != null && !etag.isBlank()) {
            xml.append("        <d:getetag>").append(escapeXml(etag)).append("</d:getetag>\n");
        }
    }

    private void appendLockProperties(StringBuilder xml) {
        xml.append("        <d:supportedlock/>\n")
                .append("        <d:lockdiscovery/>\n");
    }

    private String davHref(String path, boolean collection) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        String href = DAV_ROOT;
        if (!"/".equals(normalized)) {
            href += "/" + normalized.substring(1);
            href = encodeDavHref(href);
        }
        if (collection && !href.endsWith("/")) {
            href += "/";
        }
        return href;
    }

    private String encodeDavHref(String href) {
        StringBuilder encoded = new StringBuilder();
        boolean first = true;
        for (String segment : href.split("/", -1)) {
            if (first && segment.isEmpty()) {
                encoded.append('/');
                first = false;
                continue;
            }
            if (!first && !encoded.toString().endsWith("/")) {
                encoded.append('/');
            }
            encoded.append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
            first = false;
        }
        return encoded.toString();
    }

    private String displayName(String path) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        if ("/".equals(normalized)) {
            return "Files";
        }
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private String httpDate(OffsetDateTime value) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(value);
    }

    private String errorXml(String code, String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:error xmlns:d="DAV:">
                  <d:responsedescription>%s</d:responsedescription>
                  <weave-code>%s</weave-code>
                </d:error>
                """.formatted(escapeXml(message), escapeXml(code));
    }

    private String lockDiscoveryXml(WebDavLockResult result) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:prop xmlns:d="DAV:">
                  <d:lockdiscovery>
                    <d:activelock>
                      <d:locktype><d:write/></d:locktype>
                      <d:lockscope><d:exclusive/></d:lockscope>
                      <d:depth>0</d:depth>
                      <d:timeout>Second-%d</d:timeout>
                      <d:locktoken><d:href>%s</d:href></d:locktoken>
                      <d:lockroot><d:href>%s</d:href></d:lockroot>
                    </d:activelock>
                  </d:lockdiscovery>
                </d:prop>
                """.formatted(
                result.timeoutSeconds(),
                escapeXml(result.token()),
                escapeXml(davHref(result.path(), false)));
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
