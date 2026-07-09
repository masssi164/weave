package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileQuotaResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class NextcloudFilesAdapter implements FilesStorageAdapter {

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

    public NextcloudFilesAdapter(NextcloudFilesProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public FilesStorageReadiness readinessProbe() {
        if (!isConfigured()) {
            return FilesStorageReadiness.degraded("files-storage-not-configured");
        }
        try {
            probeRoot();
            return FilesStorageReadiness.ready();
        } catch (ApiErrorException exception) {
            return FilesStorageReadiness.degraded(readinessCode(exception.code()));
        }
    }

    @Override
    public FileListResponse list(String path) {
        return listWithVersionTokens(path).listing();
    }

    @Override
    public VersionedFileListResponse listWithVersionTokens(String path) {
        ensureConfigured();
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
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
    public String versionToken(String path) {
        ensureConfigured();
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            return restClient.method(PROPFIND)
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
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("version-token", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("version-token", exception);
        }
    }

    @Override
    public FileItemResponse createFolder(CreateFolderRequest request) {
        ensureConfigured();
        String folderPath = FilePathCodec.childPath(request.parentPath(), request.name());
        try {
            return restClient.method(MKCOL)
                    .uri(webdavUri(folderPath, false))
                    .headers(this::applyActorHeaders)
                    .exchange((webdavRequest, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return folderItem(folderPath, null);
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
    public FileUploadResponse upload(String parentPath, MultipartFile file) {
        ensureConfigured();
        String filename = FilePathCodec.validateFileName(firstText(file.getOriginalFilename(), file.getName()));
        String targetPath = FilePathCodec.childPath(parentPath, filename);
        try {
            byte[] content = file.getBytes();
            FileItemResponse item = restClient.method(HttpMethod.PUT)
                    .uri(webdavUri(targetPath, false))
                    .headers(headers -> {
                        applyActorHeaders(headers);
                        MediaType mediaType = file.getContentType() == null
                                ? MediaType.APPLICATION_OCTET_STREAM
                                : MediaType.parseMediaType(file.getContentType());
                        headers.setContentType(mediaType);
                    })
                    .body(content)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return fileItem(targetPath, file.getContentType(), (long) content.length, null);
                        }
                        throw mapStatus(response.getStatusCode(), "upload-file", targetPath);
                    });
            return new FileUploadResponse(item);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("upload-file", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("upload-file", exception);
        } catch (Exception exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "file-upload-unreadable",
                    "Uploaded file could not be read by the backend.",
                    Map.of("module", "files", "operation", "upload-file"));
        }
    }

    @Override
    public FileItemResponse put(String path, byte[] content, String mimeType) {
        ensureConfigured();
        String targetPath = FilePathCodec.normalizeProductPath(path);
        byte[] body = content == null ? new byte[0] : content;
        try {
            return restClient.method(HttpMethod.PUT)
                    .uri(webdavUri(targetPath, false))
                    .headers(headers -> {
                        applyActorHeaders(headers);
                        headers.setContentType(mediaType(mimeType));
                    })
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return fileItem(targetPath, mimeType, (long) body.length, null);
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
    public FileItemResponse copy(String sourcePath, String destinationPath, boolean overwrite) {
        return copyOrMove(COPY, "webdav-copy", sourcePath, destinationPath, overwrite);
    }

    @Override
    public FileItemResponse move(String sourcePath, String destinationPath, boolean overwrite) {
        return copyOrMove(MOVE, "webdav-move", sourcePath, destinationPath, overwrite);
    }

    @Override
    public DownloadedFile download(String id) {
        ensureConfigured();
        String path = FilePathCodec.pathFromId(id);
        try {
            return restClient.get()
                    .uri(webdavUri(path, false))
                    .headers(this::applyActorHeaders)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            byte[] body = StreamUtils.copyToByteArray(response.getBody());
                            MediaType mediaType = response.getHeaders().getContentType();
                            return new DownloadedFile(
                                    filename(path),
                                    mediaType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mediaType.toString(),
                                    body);
                        }
                        throw mapStatus(response.getStatusCode(), "download-file", path);
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("download-file", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("download-file", exception);
        }
    }

    private FileItemResponse copyOrMove(
            HttpMethod method,
            String operation,
            String sourcePath,
            String destinationPath,
            boolean overwrite) {
        ensureConfigured();
        String normalizedSource = FilePathCodec.normalizeProductPath(sourcePath);
        String normalizedDestination = FilePathCodec.normalizeProductPath(destinationPath);
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
            FileItemResponse existing = list(parentPath(normalizedDestination)).items().stream()
                    .filter(item -> FilePathCodec.normalizeProductPath(item.path()).equals(normalizedDestination))
                    .findFirst()
                    .orElse(null);
            return existing == null ? fileItem(normalizedDestination, null, null, null) : existing;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable(operation, exception);
        } catch (RestClientException exception) {
            throw downstreamFailure(operation, exception);
        }
    }

    @Override
    public void delete(String id) {
        ensureConfigured();
        String path = FilePathCodec.pathFromId(id);
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(webdavUri(path, false))
                    .headers(this::applyActorHeaders)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return null;
                        }
                        throw mapStatus(response.getStatusCode(), "delete-file", path);
                    });
        } catch (ResourceAccessException exception) {
            throw downstreamUnavailable("delete-file", exception);
        } catch (RestClientException exception) {
            throw downstreamFailure("delete-file", exception);
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
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

    private VersionedFileListResponse parseVersionedList(String listedPath, InputStream body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(body);
            NodeList responses = document.getElementsByTagNameNS("*", "response");
            List<FileItemResponse> items = new ArrayList<>();
            Map<String, String> versionTokens = new LinkedHashMap<>();
            FileQuotaResponse quota = null;
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
                OffsetDateTime modifiedAt = parseModifiedAt(childText(prop, "getlastmodified"));
                items.add(folder
                        ? folderItem(itemPath, modifiedAt)
                        : fileItem(itemPath, mimeType, size, modifiedAt));
                if (StringUtils.hasText(versionToken)) {
                    versionTokens.put(FilePathCodec.normalizeProductPath(itemPath), versionToken.trim());
                }
            }
            items.sort(Comparator
                    .comparing((FileItemResponse item) -> "folder".equals(item.type()) ? 0 : 1)
                    .thenComparing(FileItemResponse::name, String.CASE_INSENSITIVE_ORDER));
            return new VersionedFileListResponse(
                    new FileListResponse(listedPath, List.copyOf(items), quota),
                    listedVersionToken,
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

    private FileQuotaResponse quotaFrom(Element prop) {
        Long used = parseLong(childText(prop, "quota-used-bytes"));
        Long available = parseLong(childText(prop, "quota-available-bytes"));
        Long total = used != null && available != null && available >= 0 ? used + available : null;
        if (used == null && total == null) {
            return null;
        }
        return new FileQuotaResponse(used, total);
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

    private FileItemResponse folderItem(String path, OffsetDateTime modifiedAt) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        return new FileItemResponse(
                FilePathCodec.toId(normalized),
                filename(normalized),
                normalized,
                "folder",
                null,
                null,
                modifiedAt,
                false);
    }

    private FileItemResponse fileItem(String path, String mimeType, Long size, OffsetDateTime modifiedAt) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        return new FileItemResponse(
                FilePathCodec.toId(normalized),
                filename(normalized),
                normalized,
                "file",
                mimeType,
                size,
                modifiedAt,
                true);
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

    private OffsetDateTime parseModifiedAt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime();
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
            case "nextcloud-request-failed" -> "files-storage-request-failed";
            default -> "files-storage-degraded";
        };
    }

    private void probeRoot() {
        try {
            restClient.method(PROPFIND)
                    .uri(webdavUri("/", true))
                    .headers(this::applyActorHeaders)
                    .header("Depth", "0")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(PROPFIND_BODY)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 207 || response.getStatusCode().is2xxSuccessful()) {
                            return null;
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
