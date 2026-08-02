package com.massimotter.weave.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Semantic MCP projection over the Weave WebDAV Files facade. */
@Component
public final class FilesMcpProjection {
  private final FilesWebDavClient files;

  FilesMcpProjection(FilesWebDavClient files) {
    this.files = files;
  }

  @McpTool(
      name = "files.search",
      title = "Search Weave files",
      description =
          "Search authorized Weave Files metadata without exposing storage-provider identifiers.",
      generateOutputSchema = true,
      annotations =
          @McpTool.McpAnnotations(
              title = "Search files",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  public FileSearchResult search(
      @McpToolParam(description = "Case-insensitive file or path text, at most 200 characters")
          String query,
      @McpToolParam(
              description = "Optional Weave Files scope path, for example /Team",
              required = false)
          String path,
      @McpToolParam(
              description = "Optional maximum number of matches from 1 to 100",
              required = false)
          Integer limit) {
    if (query == null || query.isBlank() || query.length() > 200) {
      throw new IllegalArgumentException("query must contain between 1 and 200 characters");
    }
    int boundedLimit = limit == null ? 25 : limit;
    if (boundedLimit < 1 || boundedLimit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    List<FileMatch> matches =
        files.search(query.trim(), path, boundedLimit).stream()
            .map(
                item ->
                    new FileMatch(
                        item.canonicalFileId(),
                        resourceUri(item.canonicalFileId()),
                        item.name(),
                        item.type(),
                        item.mimeType(),
                        item.size(),
                        item.modifiedAt()))
            .toList();
    return new FileSearchResult(matches, matches.size());
  }

  @McpResource(
      name = "weave-file",
      title = "Authorized Weave file content",
      uri = "weave://files/{canonicalFileRef}",
      description = "Reads bounded content for one canonical file returned by files.search.",
      mimeType = "text/plain",
      annotations =
          @McpResource.McpAnnotations(
              audience = {McpSchema.Role.ASSISTANT},
              priority = 0.7))
  public String read(String canonicalFileRef) {
    String canonicalId = UriUtils.decode(canonicalFileRef, StandardCharsets.UTF_8);
    if (canonicalId.isBlank() || canonicalId.length() > 500) {
      throw new IllegalArgumentException("The canonical file reference is invalid");
    }
    FilesWebDavClient.FileContent content = files.read(canonicalId);
    String mimeType = content.item().mimeType();
    if (mimeType != null
        && !(mimeType.startsWith("text/")
            || "application/json".equals(mimeType)
            || "application/xml".equals(mimeType))) {
      throw new IllegalArgumentException("The file is not readable as bounded textual MCP context");
    }
    return new String(content.content(), StandardCharsets.UTF_8);
  }

  private static String resourceUri(String canonicalId) {
    return "weave://files/" + UriUtils.encodePathSegment(canonicalId, StandardCharsets.UTF_8);
  }

  public record FileSearchResult(List<FileMatch> matches, int count) {
    public FileSearchResult {
      matches = List.copyOf(matches);
    }
  }

  public record FileMatch(
      String canonicalFileId,
      String resourceUri,
      String name,
      String type,
      String mimeType,
      Long size,
      Instant modifiedAt) {}
}
