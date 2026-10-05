package com.massimotter.weave.mcp;

import com.massimotter.weave.userapi.api.FilesUserApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.FilesUserItemResponse;
import com.massimotter.weave.userapi.model.FilesUserListResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Curated Files MCP adapter over the same generated JVM User client as product E2E. */
@Component
final class FilesUserApiClient {
  private static final int MAX_CONTENT_BYTES = 262_144;
  private static final int MAX_SEARCH_ITEMS = 1_000;
  private static final int MAX_SEARCH_DEPTH = 16;

  private final FilesUserApi files;
  private final McpInvocationCredentials credentials;

  FilesUserApiClient(McpWorkloadProperties properties, McpInvocationCredentials credentials) {
    this.credentials = credentials;
    URI backend = properties.backendApiUri();
    ApiClient client =
        new ApiClient()
            .setHttpClientBuilder(
                HttpClient.newBuilder()
                    .connectTimeout(properties.requestTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER))
            .setReadTimeout(properties.requestTimeout());
    client.updateBaseUri(backend.getScheme() + "://" + backend.getRawAuthority());
    this.files = new FilesUserApi(client);
  }

  List<FileSearchItem> search(String query, String path, int limit) {
    if (query == null || query.isBlank() || query.length() > 200 || limit < 1 || limit > 100) {
      throw new IllegalArgumentException("The Files search request is invalid");
    }
    String scope = normalizePath(path);
    Map<String, String> headers = bearer();
    ArrayDeque<SearchFrame> pending = new ArrayDeque<>();
    pending.add(new SearchFrame(null, 0));
    Set<String> seen = new HashSet<>();
    List<FilesUserItemResponse> visible = new ArrayList<>();
    while (!pending.isEmpty()) {
      SearchFrame frame = pending.removeFirst();
      if (frame.depth() > MAX_SEARCH_DEPTH) {
        throw new IllegalStateException("The Files search scope exceeds the MCP depth bound");
      }
      FilesUserListResponse listing = list(frame.parentId(), headers);
      if (listing == null || listing.getItems() == null) {
        throw new IllegalStateException("The Files list response is unavailable");
      }
      for (FilesUserItemResponse item : listing.getItems()) {
        validate(item);
        if (!seen.add(item.getFileId())) {
          throw new IllegalStateException("The Files list contains duplicate references");
        }
        visible.add(item);
        if (visible.size() > MAX_SEARCH_ITEMS) {
          throw new IllegalStateException("The Files search scope exceeds the MCP item bound");
        }
        if (item.getKind() == FilesUserItemResponse.KindEnum.FOLDER) {
          pending.addLast(new SearchFrame(item.getFileId(), frame.depth() + 1));
        }
      }
    }
    if (!"/".equals(scope)
        && visible.stream().noneMatch(item -> scope.equals(item.getDisplayPath())
            && item.getKind() == FilesUserItemResponse.KindEnum.FOLDER)) {
      throw new IllegalArgumentException("The Files search scope is unavailable");
    }
    String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
    String prefix = "/".equals(scope) ? "/" : scope + "/";
    return visible.stream()
        .filter(item -> item.getDisplayPath().equals(scope)
            || item.getDisplayPath().startsWith(prefix))
        .filter(item -> item.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
            || item.getDisplayPath().toLowerCase(Locale.ROOT).contains(normalizedQuery))
        .map(FilesUserApiClient::project)
        .sorted(Comparator.comparing(FileSearchItem::path)
            .thenComparing(FileSearchItem::canonicalFileId))
        .limit(limit)
        .toList();
  }

  FileContent read(String fileId) {
    if (fileId == null || fileId.isBlank() || fileId.length() > 500) {
      throw new IllegalArgumentException("The canonical file reference is invalid");
    }
    Map<String, String> headers = bearer();
    FilesUserItemResponse before = inspect(fileId, headers);
    validate(before);
    if (before.getKind() != FilesUserItemResponse.KindEnum.FILE
        || before.getAllowedActions() == null
        || !before.getAllowedActions().contains("download")
        || before.getSize() < 0
        || before.getSize() > MAX_CONTENT_BYTES) {
      throw new IllegalArgumentException("The file is not available for bounded MCP read");
    }
    if (!textual(before.getMediaType())) {
      throw new IllegalArgumentException("The file is not readable as bounded textual MCP context");
    }
    File temporary = null;
    try {
      temporary = files.downloadFilesItemContent(fileId, null, headers);
      if (temporary == null || Files.size(temporary.toPath()) > MAX_CONTENT_BYTES) {
        throw new IllegalArgumentException("The file content exceeds the MCP read bound");
      }
      byte[] content;
      try (InputStream input = Files.newInputStream(temporary.toPath())) {
        content = input.readNBytes(MAX_CONTENT_BYTES + 1);
      }
      if (content.length > MAX_CONTENT_BYTES) {
        throw new IllegalArgumentException("The file content exceeds the MCP read bound");
      }
      FilesUserItemResponse after = inspect(fileId, headers);
      validate(after);
      if (!Objects.equals(before.getRevision(), after.getRevision())
          || !Objects.equals(before.getMediaType(), after.getMediaType())
          || !Objects.equals(before.getSize(), after.getSize())) {
        throw new IllegalStateException("The file changed during MCP read");
      }
      return new FileContent(project(after), content);
    } catch (ApiException failure) {
      throw supportSafeFailure(failure);
    } catch (IOException failure) {
      throw new IllegalStateException("The bounded Files content is unavailable");
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary.toPath());
          var directory = temporary.toPath().getParent();
          if (directory != null && directory.getFileName().toString().startsWith("swagger-gen-native")) {
            Files.deleteIfExists(directory);
          }
        } catch (IOException failure) {
          throw new IllegalStateException("The temporary Files content could not be removed");
        }
      }
    }
  }

  private FilesUserListResponse list(String parentId, Map<String, String> headers) {
    try {
      return files.listFilesItems(parentId, headers);
    } catch (ApiException failure) {
      throw supportSafeFailure(failure);
    }
  }

  private FilesUserItemResponse inspect(String fileId, Map<String, String> headers) {
    try {
      return files.getFilesItem(fileId, headers);
    } catch (ApiException failure) {
      throw supportSafeFailure(failure);
    }
  }

  private Map<String, String> bearer() {
    return Map.of("Authorization", "Bearer " + credentials.exchangedBearer());
  }

  private static void validate(FilesUserItemResponse item) {
    if (item == null || item.getFileId() == null || item.getFileId().isBlank()
        || item.getName() == null || item.getName().isBlank()
        || item.getDisplayPath() == null || !item.getDisplayPath().startsWith("/")
        || item.getKind() == null || item.getSize() == null
        || item.getRevision() == null || item.getRevision().isBlank()) {
      throw new IllegalStateException("The Files metadata response is invalid");
    }
  }

  private static FileSearchItem project(FilesUserItemResponse item) {
    Instant modified = item.getModifiedAt() == null ? null : item.getModifiedAt().toInstant();
    return new FileSearchItem(item.getFileId(), item.getDisplayPath(), item.getName(),
        item.getKind().getValue(), item.getMediaType(), item.getSize(), modified);
  }

  private static String normalizePath(String value) {
    String path = value == null || value.isBlank() ? "/" : value.trim();
    if (!path.startsWith("/") || path.contains("\\") || path.contains("//")
        || path.contains("?") || path.contains("#")
        || List.of(path.split("/")).contains("..")) {
      throw new IllegalArgumentException("The Files search path is invalid");
    }
    return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static boolean textual(String mediaType) {
    if (mediaType == null) {
      return false;
    }
    String value = mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    return value.startsWith("text/") || value.equals("application/json")
        || value.equals("application/xml");
  }

  private static IllegalStateException supportSafeFailure(ApiException failure) {
    return new IllegalStateException("Files User API rejected request: HTTP " + failure.getCode());
  }

  private record SearchFrame(String parentId, int depth) {}

  record FileSearchItem(String canonicalFileId, String path, String name, String type,
      String mimeType, Long size, Instant modifiedAt) {}

  record FileContent(FileSearchItem item, byte[] content) {
    FileContent { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
  }
}
