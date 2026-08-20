package com.massimotter.weave.mcp;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Narrow northbound WebDAV client. It never calls a raw storage provider. */
@Component
final class FilesWebDavClient {
  private static final int MAX_CONTENT_BYTES = 262_144;
  private static final Pattern SUPPORT_SAFE_ERROR_CODE = Pattern.compile("[a-z0-9-]{1,64}");

  private final URI filesUri;
  private final RestClient restClient;
  private final McpInvocationCredentials credentials;

  FilesWebDavClient(
      McpWorkloadProperties properties,
      RestClient.Builder restClientBuilder,
      McpInvocationCredentials credentials) {
    this.filesUri = properties.backendFilesUri();
    this.credentials = credentials;
    this.restClient =
        restClientBuilder
            .baseUrl(filesUri.toString())
            .defaultStatusHandler(
                status -> status.isError(),
                (request, response) -> {
                  String reported = response.getHeaders().getFirst("X-Weave-Error-Code");
                  String code =
                      reported != null && SUPPORT_SAFE_ERROR_CODE.matcher(reported).matches()
                          ? reported
                          : "files-facade-rejected";
                  throw new IllegalStateException("Files facade rejected request: " + code);
                })
            .requestInterceptor(
                (request, body, execution) -> {
                  request.getHeaders().setBearerAuth(credentials.exchangedBearer());
                  return execution.execute(request, body);
                })
            .build();
  }

  List<FileSearchItem> search(String query, String path, int limit) {
    String normalizedPath = normalizePath(path);
    String scopeHref =
        filesUri.getPath() + ("/".equals(normalizedPath) ? "" : encodePath(normalizedPath));
    String body =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:searchrequest xmlns:d="DAV:" xmlns:w="urn:weave:files">
          <d:basicsearch>
            <d:select><d:prop><w:canonical-id/><d:displayname/><d:getcontenttype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:select>
            <d:from><d:scope><d:href>%s</d:href><d:depth>infinity</d:depth></d:scope></d:from>
            <d:where><d:like><d:prop><d:displayname/></d:prop><d:literal>%s</d:literal></d:like></d:where>
            <d:limit><d:nresults>%d</d:nresults></d:limit>
          </d:basicsearch>
        </d:searchrequest>
        """
            .formatted(xml(scopeHref), xml(likeSubstringPattern(query)), limit);
    byte[] response =
        restClient
            .method(HttpMethod.valueOf("SEARCH"))
            .uri(encodePath(normalizedPath))
            .contentType(MediaType.APPLICATION_XML)
            .accept(MediaType.APPLICATION_XML)
            .body(body)
            .retrieve()
            .body(byte[].class);
    return parseSearch(response);
  }

  FileContent read(String canonicalFileId) {
    List<FileSearchItem> matches = findByCanonicalId(canonicalFileId);
    if (matches.size() != 1 || !"file".equals(matches.getFirst().type())) {
      throw new IllegalArgumentException(
          "The canonical file reference is unavailable or ambiguous");
    }
    FileSearchItem item = matches.getFirst();
    byte[] content =
        restClient
            .get()
            .uri(encodePath(item.path()))
            .accept(MediaType.ALL)
            .retrieve()
            .body(byte[].class);
    if (content == null || content.length > MAX_CONTENT_BYTES) {
      throw new IllegalArgumentException("The file content exceeds the MCP read bound");
    }
    return new FileContent(item, content);
  }

  private List<FileSearchItem> findByCanonicalId(String canonicalFileId) {
    if (canonicalFileId == null || canonicalFileId.isBlank() || canonicalFileId.length() > 500) {
      throw new IllegalArgumentException("The canonical file reference is invalid");
    }
    String body =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:searchrequest xmlns:d="DAV:" xmlns:w="urn:weave:files">
          <d:basicsearch>
            <d:select><d:prop><w:canonical-id/><d:displayname/><d:getcontenttype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:select>
            <d:from><d:scope><d:href>%s</d:href><d:depth>infinity</d:depth></d:scope></d:from>
            <d:where><d:eq><d:prop><w:canonical-id/></d:prop><d:literal>%s</d:literal></d:eq></d:where>
            <d:limit><d:nresults>2</d:nresults></d:limit>
          </d:basicsearch>
        </d:searchrequest>
        """
            .formatted(xml(filesUri.getPath()), xml(canonicalFileId));
    byte[] response =
        restClient
            .method(HttpMethod.valueOf("SEARCH"))
            .uri("")
            .contentType(MediaType.APPLICATION_XML)
            .accept(MediaType.APPLICATION_XML)
            .body(body)
            .retrieve()
            .body(byte[].class);
    return parseSearch(response);
  }

  private List<FileSearchItem> parseSearch(byte[] body) {
    if (body == null || body.length == 0 || body.length > 1_048_576) {
      throw new IllegalArgumentException("The Files search response is unavailable");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      NodeList responses =
          factory
              .newDocumentBuilder()
              .parse(new ByteArrayInputStream(body))
              .getElementsByTagNameNS("DAV:", "response");
      for (int index = 0; index < responses.getLength(); index++) {
        Element response = (Element) responses.item(index);
        if (isInsufficientStorage(directChildText(response, "DAV:", "status"))) {
          throw new IncompleteSearchResponseException();
        }
      }
      List<FileSearchItem> results = new ArrayList<>();
      for (int index = 0; index < responses.getLength(); index++) {
        Element response = (Element) responses.item(index);
        String canonicalId = required(response, "urn:weave:files", "canonical-id");
        String href = required(response, "DAV:", "href");
        String path = URI.create(href).getPath().substring(filesUri.getPath().length());
        boolean folder = response.getElementsByTagNameNS("DAV:", "collection").getLength() > 0;
        results.add(
            new FileSearchItem(
                canonicalId,
                UriUtils.decode(path, StandardCharsets.UTF_8),
                required(response, "DAV:", "displayname"),
                folder ? "folder" : "file",
                optional(response, "DAV:", "getcontenttype"),
                optionalLong(response, "DAV:", "getcontentlength"),
                optionalInstant(response, "DAV:", "getlastmodified")));
      }
      return List.copyOf(results);
    } catch (IncompleteSearchResponseException incomplete) {
      throw incomplete;
    } catch (Exception invalid) {
      throw new IllegalArgumentException("The Files search response is invalid");
    }
  }

  private static String directChildText(Element element, String namespace, String name) {
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.ELEMENT_NODE
          && namespace.equals(child.getNamespaceURI())
          && name.equals(child.getLocalName())) {
        return child.getTextContent().trim();
      }
    }
    return null;
  }

  private static boolean isInsufficientStorage(String status) {
    if (status == null) {
      return false;
    }
    String[] parts = status.trim().split("\\s+", 3);
    return parts.length >= 2 && parts[0].startsWith("HTTP/") && "507".equals(parts[1]);
  }

  private static String required(Element element, String namespace, String name) {
    String value = optional(element, namespace, name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("The Files response is missing required metadata");
    }
    return value;
  }

  private static String optional(Element element, String namespace, String name) {
    NodeList values = element.getElementsByTagNameNS(namespace, name);
    return values.getLength() == 0 ? null : values.item(0).getTextContent().trim();
  }

  private static Long optionalLong(Element element, String namespace, String name) {
    String value = optional(element, namespace, name);
    return value == null ? null : Long.valueOf(value);
  }

  private static Instant optionalInstant(Element element, String namespace, String name) {
    String value = optional(element, namespace, name);
    return value == null
        ? null
        : java.time.ZonedDateTime.parse(
                value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant();
  }

  private static String normalizePath(String path) {
    String value = path == null || path.isBlank() ? "/" : path.trim().replace('\\', '/');
    if (!value.startsWith("/")) {
      value = "/" + value;
    }
    if (value.contains("..") || value.contains("//")) {
      throw new IllegalArgumentException("The Files path is invalid");
    }
    return value.length() > 1 && value.endsWith("/")
        ? value.substring(0, value.length() - 1)
        : value;
  }

  private static String encodePath(String path) {
    if ("/".equals(path)) {
      return "";
    }
    StringBuilder encoded = new StringBuilder();
    for (String segment : path.substring(1).split("/")) {
      encoded.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
    }
    return encoded.toString();
  }

  private static String likeSubstringPattern(String query) {
    String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  record FileSearchItem(
      String canonicalFileId,
      String path,
      String name,
      String type,
      String mimeType,
      Long size,
      Instant modifiedAt) {}

  record FileContent(FileSearchItem item, byte[] content) {
    FileContent {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }

  private static final class IncompleteSearchResponseException extends IllegalArgumentException {
    private IncompleteSearchResponseException() {
      super("The Files search result is unavailable or ambiguous");
    }
  }
}
