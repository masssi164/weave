package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.controller.protocol.FilesWebDavSearchParser;
import com.massimotter.weave.backend.controller.protocol.FilesWebDavSearchParser.SearchRequestException;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.EntityTagCondition;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Egress;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.WebDavBasicSearchEvaluator;
import com.massimotter.weave.backend.service.files.WebDavFileRead;
import com.massimotter.weave.backend.service.files.WebDavPropfindListing;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavPutRequest;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchResult;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.namespace.QName;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@Hidden
public class FilesWebDavController {

    private static final String DAV_ROOT = "/dav/files";
    private static final MediaType XML = MediaType.APPLICATION_XML;
    private static final MediaType DAV_XML = new MediaType("application", "xml", StandardCharsets.UTF_8);

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
                case "SEARCH" -> search(request);
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
        } catch (SearchRequestException exception) {
            return searchError(exception);
        } catch (ApiErrorException exception) {
            return davError(exception);
        }
    }

    private ResponseEntity<Void> options() {
        boolean searchQualified = filesFacadeService.webDavSearchQualified();
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent()
                .header("DAV", "1")
                .header(HttpHeaders.ALLOW, allowedMethods(searchQualified))
                .header("MS-Author-Via", "DAV")
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (searchQualified) {
            response.header("DASL", "<DAV:basicsearch>");
        }
        return response.build();
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

    private ResponseEntity<String> search(HttpServletRequest request) {
        WebDavSearchRequest search = FilesWebDavSearchParser.parse(request);
        WebDavSearchResult result = filesFacadeService.webDavSearch(search);
        return ResponseEntity.status(207)
                .contentType(DAV_XML)
                .header("DAV", "1")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(searchMultistatus(search, result));
    }

    private ResponseEntity<?> get(HttpServletRequest request, boolean headOnly) {
        String path = productPath(request);
        WebDavFileRead file = filesFacadeService.openWebDavPath(path);
        EntityTagCondition ifMatch = entityTagCondition(
                combinedListHeader(request, HttpHeaders.IF_MATCH),
                "If-Match");
        EntityTagCondition ifNoneMatch = entityTagCondition(
                combinedListHeader(request, HttpHeaders.IF_NONE_MATCH),
                "If-None-Match");
        if (ifMatch.supplied() && !ifMatch.matches(file.strongEtag(), true)) {
            throw readPreconditionFailed("If-Match did not match the selected representation.");
        }
        if (ifNoneMatch.supplied() && ifNoneMatch.matches(file.strongEtag(), false)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(file.strongEtag())
                    .header(HttpHeaders.CACHE_CONTROL, file.cacheControl())
                    .build();
        }
        if (!file.withinContentProfile()) {
            throw readStreamingCapacityUnavailable();
        }
        try {
            MediaType.parseMediaType(file.contentType());
        } catch (IllegalArgumentException invalidStoredMediaType) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "file-content-integrity-unavailable",
                    "The selected Files representation has invalid metadata.",
                    Map.of("module", "files", "operation", "webdav-read", "diagnosticsRedacted", true));
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .contentLength(file.contentLength())
                .eTag(file.strongEtag())
                .header(HttpHeaders.CACHE_CONTROL, file.cacheControl())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename()).build().toString());
        if (headOnly) {
            return builder.build();
        }
        Egress egress = file.prepareBody();
        try {
            InputStream source = new EgressInputStream(egress.openStream(), egress);
            InputStreamResource body = new InputStreamResource(source) {
                @Override public long contentLength() { return file.contentLength(); }
                @Override public String getFilename() { return file.filename(); }
            };
            return builder.body(body);
        } catch (IOException failure) {
            egress.close();
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "file-content-integrity-unavailable",
                    "The selected Files representation could not be opened safely.",
                    Map.of("module", "files", "operation", "webdav-get", "diagnosticsRedacted", true));
        }
    }

    private ResponseEntity<Void> put(HttpServletRequest request) {
        String path = productPath(request);
        WebDavMutationResult result = filesFacadeService.putWebDavFile(
                path,
                new WebDavPutRequest(
                        Collections.list(request.getHeaders(HttpHeaders.CONTENT_LENGTH)),
                        Collections.list(request.getHeaders(HttpHeaders.CONTENT_TYPE)),
                        Collections.list(request.getHeaders(HttpHeaders.CONTENT_ENCODING)),
                        Collections.list(request.getHeaders(HttpHeaders.TRANSFER_ENCODING)),
                        request::getInputStream),
                combinedListHeader(request, HttpHeaders.IF_MATCH),
                combinedListHeader(request, HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"),
                request.getHeader("Idempotency-Key"));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), false))
                .build();
    }

    private ResponseEntity<Void> mkcol(HttpServletRequest request) {
        WebDavMutationResult result = filesFacadeService.createWebDavFolder(
                productPath(request),
                combinedListHeader(request, HttpHeaders.IF_MATCH),
                combinedListHeader(request, HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"),
                request.getHeader("Idempotency-Key"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), true))
                .build();
    }

    private ResponseEntity<Void> delete(HttpServletRequest request) {
        filesFacadeService.deleteWebDavPath(
                productPath(request),
                combinedListHeader(request, HttpHeaders.IF_MATCH),
                combinedListHeader(request, HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"),
                request.getHeader("Idempotency-Key"));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> copy(HttpServletRequest request) {
        WebDavMutationResult result = filesFacadeService.copyWebDavPath(
                productPath(request),
                destinationPath(request),
                overwrite(request),
                combinedListHeader(request, HttpHeaders.IF_MATCH),
                combinedListHeader(request, HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"),
                request.getHeader("Idempotency-Key"));
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
                combinedListHeader(request, HttpHeaders.IF_MATCH),
                combinedListHeader(request, HttpHeaders.IF_NONE_MATCH),
                request.getHeader("If"),
                request.getHeader("Idempotency-Key"));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.NO_CONTENT)
                .eTag(result.etag())
                .header(HttpHeaders.LOCATION, davHref(result.item().path(), "folder".equals(result.item().type())))
                .build();
    }

    private ResponseEntity<String> lock(HttpServletRequest request) {
        WebDavLockResult result = filesFacadeService.lockWebDavPath(
                productPath(request), request.getHeader("If"), request.getHeader("Idempotency-Key"));
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(XML)
                .header("Lock-Token", "<" + result.token() + ">")
                .header("Timeout", "Second-" + result.timeoutSeconds())
                .body(lockDiscoveryXml(result));
    }

    private ResponseEntity<Void> unlock(HttpServletRequest request) {
        filesFacadeService.unlockWebDavPath(
                productPath(request), request.getHeader("Lock-Token"), request.getHeader("Idempotency-Key"));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<String> unsupportedMethod(String method) {
        boolean searchQualified = filesFacadeService.webDavSearchQualified();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .contentType(XML)
                .header(HttpHeaders.ALLOW, allowedMethods(searchQualified))
                .header("X-Weave-Error-Code", "webdav-method-not-implemented")
                .body(errorXml("webdav-method-not-implemented",
                        "Weave Files WebDAV does not implement " + method
                                + " in the current Files protocol slice."));
    }

    private ResponseEntity<String> davError(ApiErrorException exception) {
        HttpStatus status = webdavStatus(exception);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .contentType(DAV_XML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Weave-Error-Code", exception.code());
        if ("files-content-coding-unsupported".equals(exception.code())) {
            response.header(HttpHeaders.ACCEPT_ENCODING, "identity");
        }
        return response.body(errorXml(exception.code(), exception.getMessage()));
    }

    private ResponseEntity<String> searchError(SearchRequestException exception) {
        String code = "webdav-search-invalid";
        String condition = exception.davCondition().map(value -> value.localName()).orElse(null);
        return ResponseEntity.status(exception.status())
                .contentType(DAV_XML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Weave-Error-Code", code)
                .body(searchErrorXml(code, exception.getMessage(), condition));
    }

    private String allowedMethods(boolean searchQualified) {
        return searchQualified
                ? "OPTIONS, PROPFIND, SEARCH, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK"
                : "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK";
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

    private EntityTagCondition entityTagCondition(String value, String headerName) {
        try {
            return EntityTagCondition.parseHeader(value);
        } catch (IllegalArgumentException invalid) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "files-webdav-precondition-invalid",
                    headerName + " is not a valid entity-tag condition.",
                    Map.of("module", "files", "operation", "webdav-read", "diagnosticsRedacted", true));
        }
    }

    private String combinedListHeader(HttpServletRequest request, String headerName) {
        List<String> values = Collections.list(request.getHeaders(headerName));
        return values.isEmpty() ? null : String.join(",", values);
    }

    private ApiErrorException readPreconditionFailed(String message) {
        return new ApiErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "files-precondition-failed",
                message,
                Map.of("module", "files", "operation", "webdav-read", "diagnosticsRedacted", true));
    }

    private ApiErrorException readStreamingCapacityUnavailable() {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-streaming-capacity-unavailable",
                "Bounded Files content capacity is temporarily unavailable.",
                Map.of("module", "files", "operation", "webdav-read", "diagnosticsRedacted", true));
    }

    private static final class EgressInputStream extends FilterInputStream {
        private final Egress egress;
        private boolean closed;

        private EgressInputStream(InputStream source, Egress egress) {
            super(source);
            this.egress = egress;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException failure = null;
            try {
                super.close();
            } catch (IOException closeFailure) {
                failure = closeFailure;
            } finally {
                egress.close();
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private String multistatus(WebDavPropfindListing listing, boolean includeChildren) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:w="urn:weave:files">
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

    private String searchMultistatus(WebDavSearchRequest request, WebDavSearchResult result) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:w="urn:weave:files">
                """);
        for (WebDavPropfindResource resource : result.resources()) {
            appendSearchResponse(xml, request, resource);
        }
        if (result.truncated()) {
            xml.append("  <d:response>\n")
                    .append("    <d:href>")
                    .append(escapeXml(davHref(request.arbiterPath(), false)))
                    .append("</d:href>\n")
                    .append("    <d:status>HTTP/1.1 507 Insufficient Storage</d:status>\n")
                    .append("  </d:response>\n");
        }
        xml.append("</d:multistatus>");
        return xml.toString();
    }

    private void appendSearchResponse(
            StringBuilder xml,
            WebDavSearchRequest request,
            WebDavPropfindResource resource) {
        FileItemResponse item = resource.item();
        Set<QName> requested = new LinkedHashSet<>();
        if (request.selection() instanceof WebDavSearchRequest.AllProperties) {
            requested.addAll(WebDavBasicSearchEvaluator.ALL_PROPERTIES);
        } else {
            requested.addAll(((WebDavSearchRequest.SelectedProperties) request.selection()).properties());
        }
        requested.add(WebDavBasicSearchEvaluator.CANONICAL_ID);

        StringBuilder defined = new StringBuilder();
        StringBuilder undefined = new StringBuilder();
        int propertyIndex = 0;
        for (QName property : requested) {
            if (!appendSearchProperty(defined, resource, property, propertyIndex)) {
                appendEmptyProperty(undefined, property, propertyIndex);
            }
            propertyIndex++;
        }

        xml.append("  <d:response>\n")
                .append("    <d:href>")
                .append(escapeXml(davHref(item.path(), "folder".equals(item.type()))))
                .append("</d:href>\n");
        appendSearchPropstat(xml, defined, "200 OK");
        if (!undefined.isEmpty()) {
            appendSearchPropstat(xml, undefined, "404 Not Found");
        }
        xml.append("  </d:response>\n");
    }

    private void appendSearchPropstat(StringBuilder xml, StringBuilder properties, String status) {
        xml.append("    <d:propstat>\n")
                .append("      <d:prop>\n")
                .append(properties)
                .append("      </d:prop>\n")
                .append("      <d:status>HTTP/1.1 ")
                .append(status)
                .append("</d:status>\n")
                .append("    </d:propstat>\n");
    }

    private boolean appendSearchProperty(
            StringBuilder xml,
            WebDavPropfindResource resource,
            QName property,
            int propertyIndex) {
        FileItemResponse item = resource.item();
        if (WebDavBasicSearchEvaluator.DISPLAY_NAME.equals(property)) {
            appendTextProperty(xml, "d:displayname", item.name());
        } else if (WebDavBasicSearchEvaluator.RESOURCE_TYPE.equals(property)) {
            xml.append("        <d:resourcetype>");
            if ("folder".equals(item.type())) {
                xml.append("<d:collection/>");
            }
            xml.append("</d:resourcetype>\n");
        } else if (WebDavBasicSearchEvaluator.GET_ETAG.equals(property)) {
            if (resource.etag() == null || resource.etag().isBlank()) {
                return false;
            }
            appendTextProperty(xml, "d:getetag", resource.etag());
        } else if (WebDavBasicSearchEvaluator.GET_CONTENT_TYPE.equals(property)) {
            if (!"file".equals(item.type()) || item.mimeType() == null || item.mimeType().isBlank()) {
                return false;
            }
            appendTextProperty(xml, "d:getcontenttype", item.mimeType());
        } else if (WebDavBasicSearchEvaluator.GET_CONTENT_LENGTH.equals(property)) {
            if (!"file".equals(item.type()) || item.size() == null) {
                return false;
            }
            appendTextProperty(xml, "d:getcontentlength", item.size().toString());
        } else if (WebDavBasicSearchEvaluator.GET_LAST_MODIFIED.equals(property)) {
            if (item.modifiedAt() == null) {
                return false;
            }
            appendTextProperty(xml, "d:getlastmodified", httpDate(item.modifiedAt()));
        } else if (WebDavBasicSearchEvaluator.SUPPORTED_LOCK.equals(property)) {
            xml.append("        <d:supportedlock/>\n");
        } else if (WebDavBasicSearchEvaluator.LOCK_DISCOVERY.equals(property)) {
            xml.append("        <d:lockdiscovery/>\n");
        } else if (WebDavBasicSearchEvaluator.CANONICAL_ID.equals(property)) {
            appendTextProperty(xml, "w:canonical-id", item.id());
        } else {
            return false;
        }
        return true;
    }

    private void appendTextProperty(StringBuilder xml, String elementName, String value) {
        xml.append("        <")
                .append(elementName)
                .append(">")
                .append(escapeXml(value))
                .append("</")
                .append(elementName)
                .append(">\n");
    }

    private void appendEmptyProperty(StringBuilder xml, QName property, int propertyIndex) {
        if (WebDavBasicSearchEvaluator.DAV_NAMESPACE.equals(property.getNamespaceURI())) {
            xml.append("        <d:").append(property.getLocalPart()).append("/>\n");
            return;
        }
        if (WebDavBasicSearchEvaluator.WEAVE_FILES_NAMESPACE.equals(property.getNamespaceURI())) {
            xml.append("        <w:").append(property.getLocalPart()).append("/>\n");
            return;
        }
        if (property.getNamespaceURI().isEmpty()) {
            xml.append("        <").append(property.getLocalPart()).append("/>\n");
            return;
        }
        String prefix = "p" + propertyIndex;
        xml.append("        <")
                .append(prefix)
                .append(':')
                .append(property.getLocalPart())
                .append(" xmlns:")
                .append(prefix)
                .append("=\"")
                .append(escapeXml(property.getNamespaceURI()))
                .append("\"/>\n");
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
        appendCanonicalId(xml, item.id());
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
        appendCanonicalId(xml, item.id());
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

    private void appendCanonicalId(StringBuilder xml, String canonicalId) {
        xml.append("        <w:canonical-id>")
                .append(escapeXml(canonicalId))
                .append("</w:canonical-id>\n");
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

    private String searchErrorXml(String code, String message, String condition) {
        String conditionElement = condition == null || condition.isBlank()
                ? ""
                : "  <d:" + condition + "/>\n";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:error xmlns:d="DAV:">
                %s  <d:responsedescription>%s</d:responsedescription>
                  <weave-code>%s</weave-code>
                </d:error>
                """.formatted(conditionElement, escapeXml(message), escapeXml(code));
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
