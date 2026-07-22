package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileListing;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileQuota;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderCapabilityState;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.portability.RetryAfterParser;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class NextcloudFilesAdapter implements FilesProviderPort {

    private static final HttpMethod PROPFIND = HttpMethod.valueOf("PROPFIND");
    private static final HttpMethod MKCOL = HttpMethod.valueOf("MKCOL");
    private static final HttpMethod COPY = HttpMethod.valueOf("COPY");
    private static final HttpMethod MOVE = HttpMethod.valueOf("MOVE");

    private static final String PROPFIND_BODY = """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <d:propfind xmlns:d=\"DAV:\">
              <d:prop>
                <d:resourcetype />
                <d:getcontentlength />
                <d:getcontenttype />
                <d:getlastmodified />
                <d:getetag />
                <d:quota-used-bytes />
                <d:quota-available-bytes />
              </d:prop>
            </d:propfind>
            """;

    private final NextcloudFilesProperties properties;
    private final RestClient restClient;

    @Autowired
    public NextcloudFilesAdapter(NextcloudFilesProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder
                .requestFactory(new JdkClientHttpRequestFactory())
                .build());
    }

    NextcloudFilesAdapter(NextcloudFilesProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public boolean configured() {
        return properties.isConfigured();
    }

    @Override
    public ProviderReadiness readiness() {
        ProviderCapabilityProbeResult result = healthProbe();
        return result.state() == ProviderCapabilityState.AVAILABLE
                ? ProviderReadiness.ready(result.supportSafeCode())
                : ProviderReadiness.degraded(result.supportSafeCode());
    }

    @Override
    public ProviderCapabilityProbeResult healthProbe() {
        if (!configured()) {
            return ProviderCapabilityProbeResult.unavailable("files-storage-not-configured");
        }
        try {
            return probeRoot();
        } catch (ApiErrorException exception) {
            String code = readinessCode(exception.code());
            return switch (exception.code()) {
                case "nextcloud-adapter-not-configured",
                        "nextcloud-auth-failed",
                        "files-permission-denied",
                        "file-not-found" ->
                        ProviderCapabilityProbeResult.unavailable(code);
                default -> ProviderCapabilityProbeResult.degraded(code);
            };
        }
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "files",
                "nextcloud-webdav",
                Set.of("list", "read", "write", "create_collection", "delete", "copy", "move", "versions", "quota"),
                Map.of(
                        "path", MappingClass.PORTABLE,
                        "content", MappingClass.PORTABLE,
                        "mediaType", MappingClass.PORTABLE,
                        "version", MappingClass.PORTABLE,
                        "lock", MappingClass.MANUAL_REVIEW,
                        "share", MappingClass.LOSSY),
                true,
                true,
                true);
    }

    @Override
    public VersionedListing list(FilePath path) {
        ensureConfigured();
        String normalizedPath = path.value();
        try {
            return restClient.method(PROPFIND)
                    .uri(webdavUri(normalizedPath, true))
                    .headers(this::applyActorHeaders)
                    .header("Depth", "1")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(PROPFIND_BODY)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 207 || response.getStatusCode().is2xxSuccessful()) {
                            return parseVersionedList(normalizedPath, response.getBody());
                        }
                        throw mapStatus(response.getStatusCode(), "list-files", normalizedPath);
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("list-files", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("list-files", exception);
        }
    }

    @Override
    public Optional<VersionedFile> find(FilePath path) {
        if (path.root()) {
            return Optional.of(new VersionedFile(collectionObject(path, null), version(path)));
        }
        VersionedListing listing = list(new FilePath(parentPath(path.value())));
        return listing.listing().children().stream()
                .filter(item -> item.path().equals(path))
                .findFirst()
                .map(item -> new VersionedFile(
                        item,
                        listing.childVersions().getOrDefault(path, FileVersion.unknown())));
    }

    private FileVersion version(FilePath path) {
        ensureConfigured();
        String normalizedPath = path.value();
        try {
            String token = restClient.method(PROPFIND)
                    .uri(webdavUri(normalizedPath, false))
                    .headers(this::applyActorHeaders)
                    .header("Depth", "0")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(PROPFIND_BODY)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 207 || response.getStatusCode().is2xxSuccessful()) {
                            return parseVersionToken(response.getBody());
                        }
                        throw mapStatus(response.getStatusCode(), "version-token", normalizedPath);
                    });
            return new FileVersion(token);
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("version-token", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("version-token", exception);
        }
    }

    @Override
    public FileObject createCollection(FilePath path) {
        ensureConfigured();
        String folderPath = path.value();
        try {
            return restClient.method(MKCOL)
                    .uri(webdavUri(folderPath, false))
                    .headers(this::applyActorHeaders)
                    .exchange((webdavRequest, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return collectionObject(path, null);
                        }
                        throw mapStatus(response.getStatusCode(), "create-folder", folderPath);
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("create-folder", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("create-folder", exception);
        }
    }

    @Override
    public FileObject write(FileWrite write) {
        ensureConfigured();
        String targetPath = write.path().value();
        byte[] body = write.bytes();
        try {
            return restClient.method(HttpMethod.PUT)
                    .uri(webdavUri(targetPath, false))
                    .headers(headers -> {
                        applyActorHeaders(headers);
                        headers.setContentType(mediaType(write.mediaType()));
                    })
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return fileObject(write.path(), write.mediaType(), body.length, null);
                        }
                        throw mapStatus(response.getStatusCode(), "webdav-put", targetPath);
                    });
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("webdav-put", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("webdav-put", exception);
        }
    }

    @Override
    public FileObject copy(FilePath source, FilePath destination, boolean overwrite) {
        return copyOrMove(COPY, "webdav-copy", source, destination, overwrite);
    }

    @Override
    public FileObject move(FilePath source, FilePath destination, boolean overwrite) {
        return copyOrMove(MOVE, "webdav-move", source, destination, overwrite);
    }

    @Override
    public FileContent read(FileId id) {
        ensureConfigured();
        FilePath path = new FilePath(FilePathCodec.pathFromId(id.value()));
        try {
            return restClient.get()
                    .uri(webdavUri(path.value(), false))
                    .headers(this::applyActorHeaders)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            byte[] body = StreamUtils.copyToByteArray(response.getBody());
                            MediaType mediaType = response.getHeaders().getContentType();
                            String contentType = mediaType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mediaType.toString();
                            return new FileContent(fileObject(path, contentType, body.length, null), body);
                        }
                        throw mapStatus(response.getStatusCode(), "download-file", path.value());
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("download-file", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("download-file", exception);
        }
    }

    private FileObject copyOrMove(
            HttpMethod method,
            String operation,
            FilePath sourcePath,
            FilePath destinationPath,
            boolean overwrite) {
        ensureConfigured();
        String normalizedSource = sourcePath.value();
        String normalizedDestination = destinationPath.value();
        try {
            restClient.method(method)
                    .uri(webdavUri(normalizedSource, false))
                    .headers(headers -> {
                        applyActorHeaders(headers);
                        headers.set("Destination", webdavUri(normalizedDestination, false).toString());
                        headers.set("Overwrite", overwrite ? "T" : "F");
                    })
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return null;
                        }
                        throw mapStatus(response.getStatusCode(), operation, normalizedSource);
                    });
            FileObject existing = list(new FilePath(parentPath(normalizedDestination))).listing().children().stream()
                    .filter(item -> item.path().value().equals(normalizedDestination))
                    .findFirst()
                    .orElse(null);
            return existing == null ? fileObject(destinationPath, null, 0, null) : existing;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable(operation, exception);
        } catch (RestClientException exception) {
            throw downstreamFailure(operation, exception);
        }
    }

    @Override
    public void delete(FilePath path, FileVersion expectedVersion) {
        ensureConfigured();
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(webdavUri(path.value(), false))
                    .headers(this::applyActorHeaders)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return null;
                        }
                        throw mapStatus(response.getStatusCode(), "delete-file", path.value());
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("delete-file", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("delete-file", exception);
        }
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "nextcloud-adapter-not-configured",
                    "Files facade is available, but the downstream Nextcloud adapter is not configured yet.",
                    Map.of(
                            "module", "files",
                            "actorModel", properties.actorModel(),
                            "supportedActorModels", List.of("backend-service-account")));
        }
    }

    private URI webdavUri(String productPath, boolean trailingSlashForRoot) {
        String actor = UriUtils.encodePathSegment(properties.actorUsername(), StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(properties.baseUri().toString())
                .append(properties.webdavRootPath())
                .append('/')
                .append(actor)
                .append(FilePathCodec.encodeWebdavPath(productPath));
        if (trailingSlashForRoot && "/".equals(productPath)) {
            builder.append('/');
        }
        return URI.create(builder.toString());
    }

    private void applyActorHeaders(HttpHeaders headers) {
        headers.setBasicAuth(properties.actorUsername(), properties.actorToken(), StandardCharsets.UTF_8);
        headers.set(HttpHeaders.ACCEPT, "application/xml, */*");
        headers.set("OCS-APIRequest", "true");
    }

    private VersionedListing parseVersionedList(String listedPath, InputStream body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(body);
            NodeList responses = document.getElementsByTagNameNS("*", "response");
            List<FileObject> items = new ArrayList<>();
            Map<FilePath, FileVersion> versionTokens = new LinkedHashMap<>();
            FileQuota quota = null;
            String listedVersionToken = null;
            for (int index = 0; index < responses.getLength(); index++) {
                Element response = (Element) responses.item(index);
                String itemPath = productPathFromHref(firstText(childText(response, "href"), "/"));
                Element prop = firstElement(response, "prop");
                if (prop == null) {
                    continue;
                }
                String versionToken = childText(prop, "getetag");
                if (FilePathCodec.normalizeProductPath(itemPath).equals(listedPath)) {
                    quota = quotaFrom(prop);
                    listedVersionToken = versionToken;
                    continue;
                }
                boolean folder = firstElement(prop, "collection") != null;
                Long size = folder ? null : parseLong(childText(prop, "getcontentlength"));
                String mimeType = folder ? null : childText(prop, "getcontenttype");
                Instant modifiedAt = parseModifiedAt(childText(prop, "getlastmodified"));
                FilePath path = new FilePath(itemPath);
                items.add(folder
                        ? collectionObject(path, modifiedAt)
                        : fileObject(path, mimeType, size == null ? 0 : size, modifiedAt));
                if (StringUtils.hasText(versionToken)) {
                    versionTokens.put(path, new FileVersion(versionToken));
                }
            }
            items.sort(Comparator
                    .comparing((FileObject item) -> item.kind() == Kind.COLLECTION ? 0 : 1)
                    .thenComparing(FileObject::name, String.CASE_INSENSITIVE_ORDER));
            return new VersionedListing(
                    new FileListing(new FilePath(listedPath), items, quota),
                    new FileVersion(listedVersionToken),
                    Map.copyOf(versionTokens));
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_GATEWAY,
                    "nextcloud-response-invalid",
                    "Nextcloud returned a files response the backend could not parse.",
                    Map.of("module", "files", "operation", "list-files"));
        }
    }

    private FileQuota quotaFrom(Element prop) {
        Long used = parseLong(childText(prop, "quota-used-bytes"));
        Long available = parseAvailableQuota(childText(prop, "quota-available-bytes"));
        return used == null && available == null ? FileQuota.unknown() : new FileQuota(available, used);
    }

    private Long parseAvailableQuota(String value) {
        Long available = parseLong(value);
        return available == null || available < 0 ? null : available;
    }

    private String parseVersionToken(InputStream body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(body);
            NodeList responses = document.getElementsByTagNameNS("*", "response");
            if (responses.getLength() == 0) {
                return null;
            }
            Element prop = firstElement((Element) responses.item(0), "prop");
            String etag = prop == null ? null : childText(prop, "getetag");
            return StringUtils.hasText(etag) ? etag.trim() : null;
        } catch (Exception exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_GATEWAY,
                    "nextcloud-response-invalid",
                    "Nextcloud returned a files response the backend could not parse.",
                    Map.of("module", "files", "operation", "version-token"));
        }
    }

    private String productPathFromHref(String href) {
        String rawPath = href;
        try {
            rawPath = URI.create(href).getRawPath();
        } catch (IllegalArgumentException ignored) {
            // Some WebDAV servers return already-decoded relative hrefs. Decode below if possible.
        }
        String decodedPath = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        String rootPrefix = properties.webdavRootPath() + "/" + properties.actorUsername();
        String relative = decodedPath.startsWith(rootPrefix)
                ? decodedPath.substring(rootPrefix.length())
                : decodedPath;
        if (relative.isBlank()) {
            return "/";
        }
        if (!relative.startsWith("/")) {
            relative = "/" + relative;
        }
        return FilePathCodec.normalizeProductPath(relative);
    }

    private FileObject collectionObject(FilePath path, Instant modifiedAt) {
        return new FileObject(
                new FileId(FilePathCodec.toId(path.value())),
                path,
                Kind.COLLECTION,
                0,
                null,
                modifiedAt,
                false);
    }

    private FileObject fileObject(FilePath path, String mimeType, long size, Instant modifiedAt) {
        return new FileObject(
                new FileId(FilePathCodec.toId(path.value())),
                path,
                Kind.FILE,
                size,
                mimeType,
                modifiedAt,
                false);
    }

    private String filename(String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return "/";
        }
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    private String parentPath(String path) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        int separator = normalized.lastIndexOf('/');
        return separator <= 0 ? "/" : normalized.substring(0, separator);
    }

    private Element firstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    private String childText(Element parent, String localName) {
        Element child = firstElement(parent, localName);
        if (child == null) {
            return null;
        }
        return child.getTextContent() == null ? null : child.getTextContent().trim();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Instant parseModifiedAt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : fallback;
    }

    private MediaType mediaType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private ApiErrorException mapStatus(HttpStatusCode status, String operation, String path) {
        int value = status.value();
        if (value == 401) {
            return new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "nextcloud-auth-failed",
                    "Files storage authentication failed. Ask an admin to check the backend Nextcloud actor configuration.",
                    details(operation, path, value));
        }
        if (value == 403) {
            return new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "files-permission-denied",
                    "You do not have permission to access this file or folder.",
                    details(operation, path, value));
        }
        if (value == 404) {
            return new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "file-not-found",
                    "The requested file or folder was not found.",
                    details(operation, path, value));
        }
        if (value == 409 || value == 412 || value == 423 || value == 405) {
            return new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "file-conflict",
                    "The file operation conflicts with the current storage state.",
                    details(operation, path, value));
        }
        if (value == 507) {
            return new ApiErrorException(
                    HttpStatus.INSUFFICIENT_STORAGE,
                    "files-quota-exceeded",
                    "There is not enough storage available for this file operation.",
                    details(operation, path, value));
        }
        if (value == 429) {
            return new ApiErrorException(
                    HttpStatus.BAD_GATEWAY,
                    "nextcloud-rate-limited",
                    "Files storage is temporarily rate limited.",
                    details(operation, path, value));
        }
        if (value >= 500) {
            return new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "nextcloud-unavailable",
                    "Files storage is temporarily unavailable.",
                    details(operation, path, value));
        }
        return new ApiErrorException(
                HttpStatus.BAD_GATEWAY,
                "nextcloud-request-failed",
                "Files storage rejected the backend request.",
                details(operation, path, value));
    }

    private ApiErrorException downstreamUnavailable(String operation, Exception exception) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "nextcloud-unavailable",
                "Files storage is temporarily unavailable.",
                Map.of("module", "files", "operation", operation, "reason", exception.getClass().getSimpleName()));
    }

    private ApiErrorException downstreamFailure(String operation, Exception exception) {
        return new ApiErrorException(
                HttpStatus.BAD_GATEWAY,
                "nextcloud-request-failed",
                "Files storage request failed before it could be completed.",
                Map.of("module", "files", "operation", operation, "reason", exception.getClass().getSimpleName()));
    }

    private String readinessCode(String adapterCode) {
        return switch (adapterCode) {
            case "files-permission-denied" -> "files-storage-permission-denied";
            case "file-not-found" -> "files-storage-root-missing";
            case "file-conflict" -> "files-storage-conflict";
            case "files-quota-exceeded" -> "files-storage-quota-exceeded";
            case "nextcloud-adapter-not-configured" -> "files-storage-not-configured";
            case "nextcloud-auth-failed" -> "files-storage-auth-failed";
            case "nextcloud-response-invalid" -> "files-storage-response-invalid";
            case "nextcloud-unavailable" -> "files-storage-unavailable";
            case "nextcloud-rate-limited" -> "files-storage-rate-limited";
            case "nextcloud-request-failed" -> "files-storage-request-failed";
            default -> "files-storage-degraded";
        };
    }

    private ProviderCapabilityProbeResult probeRoot() {
        try {
            return restClient.method(PROPFIND)
                    .uri(webdavUri("/", true))
                    .headers(this::applyActorHeaders)
                    .header("Depth", "0")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(PROPFIND_BODY)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 207 || response.getStatusCode().is2xxSuccessful()) {
                            return ProviderCapabilityProbeResult.available("files-storage-ready");
                        }
                        if (response.getStatusCode().value() == 429) {
                            return ProviderCapabilityProbeResult.degraded(
                                    "files-storage-rate-limited",
                                    RetryAfterParser.parse(
                                            response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                                            Instant.now()));
                        }
                        throw mapStatus(response.getStatusCode(), "probe-files-root", "/");
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("probe-files-root", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("probe-files-root", exception);
        }
    }

    private Map<String, Object> details(String operation, String path, int downstreamStatus) {
        return Map.of(
                "module", "files",
                "operation", operation,
                "path", path,
                "downstreamStatus", downstreamStatus);
    }
}
