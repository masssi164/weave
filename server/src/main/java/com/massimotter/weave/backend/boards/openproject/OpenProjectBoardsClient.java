package com.massimotter.weave.backend.boards.openproject;

import com.fasterxml.jackson.databind.JsonNode;
import com.massimotter.weave.backend.boards.port.BoardPage;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Minimal authenticated OpenProject API reader for the fail-closed read-sync seam.
 * It deliberately returns adapter-side snapshots only; provider raw JSON and
 * credentials never cross into the Weave product API contract.
 */
final class OpenProjectBoardsClient {

    private static final Pattern ID_FROM_LINK = Pattern.compile(".*/(\\d+)$");
    private static final String CURSOR_PREFIX = "op:v1:";
    private static final String API_USER = "apikey";

    private final URI baseUrl;
    private final String apiToken;
    private final RestClient restClient;

    OpenProjectBoardsClient(URI baseUrl, String apiToken, RestClient.Builder restClientBuilder) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.restClient = restClientBuilder == null ? RestClient.builder().build() : restClientBuilder.build();
    }

    boolean isConfigured() {
        return baseUrl != null && baseUrl.isAbsolute() && !apiToken.isBlank();
    }

    BoardPage<OpenProjectProjectSnapshot> listProjects(BoardQuery query) {
        requireConfigured("list-projects");
        BoardQuery effectiveQuery = query == null ? BoardQuery.firstPage() : query;
        JsonNode root = get("/api/v3/projects", Map.of(
                "pageSize", Integer.toString(effectiveQuery.limit()),
                "offset", offset(effectiveQuery.cursor(), "projects", "list-projects")), "list-projects");
        List<OpenProjectProjectSnapshot> projects = elements(root).stream().map(this::project).toList();
        return new BoardPage<>(projects, nextCursor(root, effectiveQuery.limit(), "projects"));
    }

    Optional<OpenProjectProjectSnapshot> findProject(long projectId) {
        requireConfigured("find-project");
        try {
            return Optional.of(project(get("/api/v3/projects/" + projectId, Map.of(), "find-project")));
        } catch (BoardsException exception) {
            if (exception.code() == BoardsErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    BoardPage<OpenProjectStatusSnapshot> listStatuses(BoardQuery query) {
        requireConfigured("list-statuses");
        BoardQuery effectiveQuery = query == null ? BoardQuery.firstPage() : query;
        JsonNode root = get("/api/v3/statuses", Map.of(
                "pageSize", Integer.toString(effectiveQuery.limit()),
                "offset", offset(effectiveQuery.cursor(), "statuses", "list-statuses")), "list-statuses");
        List<OpenProjectStatusSnapshot> statuses = elements(root).stream().map(this::status).toList();
        return new BoardPage<>(statuses, nextCursor(root, effectiveQuery.limit(), "statuses"));
    }

    BoardPage<OpenProjectWorkPackageSnapshot> listWorkPackages(long projectId, TaskQuery query) {
        requireConfigured("list-tasks");
        TaskQuery effectiveQuery = query == null ? TaskQuery.all() : query;
        String cursorCollection = "work-packages:" + projectId;
        String filters = "[{\"project\":{\"operator\":\"=\",\"values\":[\"" + projectId + "\"]}}]";
        JsonNode root = get("/api/v3/work_packages", Map.of(
                "filters", filters,
                "pageSize", Integer.toString(effectiveQuery.limit()),
                "offset", offset(effectiveQuery.cursor(), cursorCollection, "list-tasks")), "list-tasks");
        List<OpenProjectWorkPackageSnapshot> tasks = elements(root).stream().map(this::workPackage).toList();
        return new BoardPage<>(tasks, nextCursor(root, effectiveQuery.limit(), cursorCollection));
    }

    Optional<OpenProjectWorkPackageSnapshot> findWorkPackage(long workPackageId) {
        requireConfigured("find-task");
        try {
            return Optional.of(workPackage(get("/api/v3/work_packages/" + workPackageId, Map.of(), "find-task")));
        } catch (BoardsException exception) {
            if (exception.code() == BoardsErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    OpenProjectWorkPackageSnapshot updateWorkPackage(
            long workPackageId,
            String lockVersion,
            Long statusId,
            Integer position,
            String operation) {
        requireConfigured(operation);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("lockVersion", parseLockVersion(lockVersion, operation));
        if (statusId != null) {
            body.put("_links", Map.of("status", Map.of("href", "/api/v3/statuses/" + statusId)));
        }
        if (position != null) {
            body.put("position", position);
        }
        return workPackage(patch("/api/v3/work_packages/" + workPackageId, body, operation));
    }

    private JsonNode get(String path, Map<String, String> queryParams, String operation) {
        URI uri = uri(path, queryParams);
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw httpError(operation, exception, "read-sync");
        } catch (ResourceAccessException exception) {
            throw new BoardsException(
                    BoardsErrorCode.OFFLINE,
                    "OpenProject Boards read-sync could not reach the provider runtime.",
                    details(operation, "offline"));
        } catch (RuntimeException exception) {
            throw new BoardsException(
                    BoardsErrorCode.UNKNOWN,
                    "OpenProject Boards read-sync failed with a support-safe provider error.",
                    details(operation, "unknown"));
        }
    }

    private JsonNode patch(String path, Map<String, Object> body, String operation) {
        URI uri = uri(path, Map.of());
        try {
            return restClient.patch()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw httpError(operation, exception, "write");
        } catch (ResourceAccessException exception) {
            throw new BoardsException(
                    BoardsErrorCode.OFFLINE,
                    "OpenProject Boards write could not reach the provider runtime.",
                    details(operation, "offline"));
        } catch (RuntimeException exception) {
            throw new BoardsException(
                    BoardsErrorCode.UNKNOWN,
                    "OpenProject Boards write failed with a support-safe provider error.",
                    details(operation, "unknown"));
        }
    }

    private long parseLockVersion(String lockVersion, String operation) {
        if (lockVersion == null || lockVersion.isBlank()) {
            throw new BoardsException(
                    BoardsErrorCode.CONFLICT,
                    "OpenProject Boards write was blocked because the provider lock version was unavailable.",
                    details(operation, "missing_lock_version"));
        }
        try {
            return Long.parseLong(lockVersion);
        } catch (NumberFormatException exception) {
            throw new BoardsException(
                    BoardsErrorCode.CONFLICT,
                    "OpenProject Boards write was blocked because the provider lock version was invalid.",
                    details(operation, "invalid_lock_version"));
        }
    }

    private URI uri(String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUrl).path(path);
        queryParams.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private String basicAuthHeader() {
        return "Basic " + HttpHeaders.encodeBasicAuth(API_USER, apiToken, java.nio.charset.StandardCharsets.UTF_8);
    }

    private BoardsException httpError(String operation, RestClientResponseException exception, String mode) {
        BoardsErrorCode code = switch (exception.getStatusCode().value()) {
            case 401 -> BoardsErrorCode.UNAUTHORIZED;
            case 403 -> BoardsErrorCode.FORBIDDEN;
            case 404 -> BoardsErrorCode.NOT_FOUND;
            case 409 -> BoardsErrorCode.CONFLICT;
            case 429 -> BoardsErrorCode.RATE_LIMITED;
            default -> exception.getStatusCode().is5xxServerError()
                    ? BoardsErrorCode.PROVIDER_UNAVAILABLE
                    : BoardsErrorCode.UNKNOWN;
        };
        return new BoardsException(
                code,
                "OpenProject Boards " + mode + " failed with a support-safe provider response.",
                details(operation, code.contractName(), mode));
    }

    private void requireConfigured(String operation) {
        if (!isConfigured()) {
            throw new BoardsException(
                    BoardsErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenProject Boards read-sync requires a provider base URL and backend-held API token.",
                    details(operation, "provider_configuration"));
        }
    }

    private Map<String, String> details(String operation, String reason) {
        return details(operation, reason, null);
    }

    private Map<String, String> details(String operation, String reason, String mode) {
        var details = new java.util.LinkedHashMap<String, String>();
        details.put("provider", "openproject");
        details.put("operation", operation);
        details.put("reason", reason);
        if (mode != null && !mode.isBlank()) {
            details.put("mode", mode);
        }
        details.put("supportSafe", "true");
        return details;
    }

    private OpenProjectProjectSnapshot project(JsonNode node) {
        long id = node.path("id").asLong();
        String identifier = text(node, "identifier").orElse("project-" + id);
        return new OpenProjectProjectSnapshot(
                id,
                identifier,
                text(node, "name").orElse(identifier),
                richText(node.path("description")),
                node.path("active").isBoolean() ? !node.path("active").asBoolean() : node.path("archived").asBoolean(false),
                webUri("/projects/" + identifier),
                instant(text(node, "updatedAt").orElse(null)).orElse(null));
    }

    private OpenProjectStatusSnapshot status(JsonNode node) {
        long id = node.path("id").asLong();
        return new OpenProjectStatusSnapshot(
                id,
                text(node, "name").orElse("Status " + id),
                node.path("position").asInt(0),
                node.path("isClosed").asBoolean(node.path("closed").asBoolean(false)),
                null);
    }

    private OpenProjectWorkPackageSnapshot workPackage(JsonNode node) {
        long id = node.path("id").asLong();
        long projectId = linkId(node, "project").orElse(0L);
        long statusId = linkId(node, "status").orElse(0L);
        return new OpenProjectWorkPackageSnapshot(
                id,
                projectId,
                statusId,
                text(node, "subject").orElse("Work package " + id),
                richText(node.path("description")),
                node.path("position").asInt(0),
                linkTitle(node, "priority").orElse(null),
                linkTitle(node, "assignee").map(value -> List.of("user:" + value)).orElse(List.of()),
                List.of(),
                dateOrInstant(text(node, "startDate").orElse(null)).orElse(null),
                dateOrInstant(text(node, "dueDate").orElse(null)).orElse(null),
                dateOrInstant(text(node, "closedAt").orElse(null)).orElse(null),
                dateOrInstant(text(node, "updatedAt").orElse(null)).orElse(null),
                webUri("/work_packages/" + id),
                text(node, "lockVersion").or(() -> node.path("lockVersion").isNumber()
                        ? Optional.of(Long.toString(node.path("lockVersion").asLong()))
                        : Optional.empty()).orElse(null));
    }

    private List<JsonNode> elements(JsonNode root) {
        JsonNode elements = root == null ? null : root.path("_embedded").path("elements");
        if (elements == null || !elements.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        elements.forEach(values::add);
        return values;
    }

    private String nextCursor(JsonNode root, int fallbackLimit, String collection) {
        int total = root == null ? 0 : root.path("total").asInt(0);
        int count = root == null ? 0 : root.path("count").asInt(0);
        int offset = root == null ? 1 : root.path("offset").asInt(1);
        int pageSize = root == null ? fallbackLimit : root.path("pageSize").asInt(fallbackLimit);
        int consumed = count > 0 ? count : pageSize;
        int next = offset + consumed;
        return total >= next ? encodeCursor(collection, next) : null;
    }

    private String offset(String cursor, String collection, String operation) {
        if (cursor == null || cursor.isBlank()) {
            return "1";
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor.substring(CURSOR_PREFIX.length())), StandardCharsets.UTF_8);
            int split = payload.lastIndexOf(':');
            if (!cursor.startsWith(CURSOR_PREFIX) || split < 1) {
                throw new IllegalArgumentException("invalid cursor envelope");
            }
            String cursorCollection = payload.substring(0, split);
            int cursorOffset = Integer.parseInt(payload.substring(split + 1));
            if (!collection.equals(cursorCollection) || cursorOffset < 1) {
                throw new IllegalArgumentException("cursor collection mismatch");
            }
            return Integer.toString(cursorOffset);
        } catch (RuntimeException exception) {
            throw new BoardsException(
                    BoardsErrorCode.VALIDATION,
                    "OpenProject Boards read-sync received an invalid support-safe pagination cursor.",
                    details(operation, "cursor_validation"));
        }
    }

    private String encodeCursor(String collection, int offset) {
        String payload = collection + ":" + offset;
        return CURSOR_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<Long> linkId(JsonNode node, String name) {
        String href = node.path("_links").path(name).path("href").asText(null);
        if (href == null) {
            return Optional.empty();
        }
        var matcher = ID_FROM_LINK.matcher(href);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(matcher.group(1)));
    }

    private Optional<String> linkTitle(JsonNode node, String name) {
        return text(node.path("_links").path(name), "title");
    }

    private String richText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return text(node, "raw").or(() -> text(node, "html")).orElse(null);
    }

    private Optional<String> text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(value.asText()).filter(text -> !text.isBlank());
    }

    private Optional<Instant> dateOrInstant(String value) {
        Optional<Instant> parsed = instant(value);
        if (parsed.isPresent() || value == null || value.isBlank()) {
            return parsed;
        }
        try {
            return Optional.of(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<Instant> instant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private URI webUri(String path) {
        return UriComponentsBuilder.fromUri(baseUrl).replacePath(path).replaceQuery(null).build().toUri();
    }
}
