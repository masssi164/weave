package com.massimotter.weave.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class McpRequestAdmissionFilter extends OncePerRequestFilter {
  static final String EXCHANGED_TOKEN_ATTRIBUTE = ExchangedAccessToken.class.getName();
  private static final String DOMAIN_SCOPE_ATTRIBUTE =
      McpRequestAdmissionFilter.class.getName() + ".domain-scope";

  private final McpWorkloadProperties properties;
  private final McpWorkloadTokenPolicy tokenPolicy;
  private final McpBackendTokenExchange exchange;
  private final McpBearerChallengeWriter challenges;
  private final JsonMapper mapper;

  McpRequestAdmissionFilter(
      McpWorkloadProperties properties, McpBackendTokenExchange exchange, JsonMapper mapper) {
    this.properties = properties;
    this.tokenPolicy = new McpWorkloadTokenPolicy(properties);
    this.exchange = exchange;
    this.challenges = new McpBearerChallengeWriter(properties, mapper);
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !("/mcp".equals(path) || path.startsWith("/mcp/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
        || !authentication.isAuthenticated()) {
      challenges.unauthorized(response);
      return;
    }
    try {
      McpCellWorkloadPrincipal workload = tokenPolicy.resolve(jwtAuthentication.getToken());
      HttpServletRequest effectiveRequest = validateExtensionNegotiation(request);
      Set<String> admittedScopes =
          workload.scopes().stream()
              .filter(properties.exchangeScopes()::contains)
              .collect(Collectors.toUnmodifiableSet());
      Object requestedDomain = effectiveRequest.getAttribute(DOMAIN_SCOPE_ATTRIBUTE);
      Set<String> scopes = admittedScopes;
      if (requestedDomain instanceof String domain) {
        if (!admittedScopes.contains(domain)) {
          throw new McpAdmissionException(McpAdmissionException.Kind.INSUFFICIENT_SCOPE);
        }
        scopes = Set.of(domain);
      }
      ExchangedAccessToken exchanged =
          exchange.exchange(workload, jwtAuthentication.getToken().getTokenValue(), scopes);
      effectiveRequest.setAttribute(EXCHANGED_TOKEN_ATTRIBUTE, exchanged);
      filterChain.doFilter(effectiveRequest, response);
    } catch (McpAdmissionException failure) {
      switch (failure.kind()) {
        case INSUFFICIENT_SCOPE -> challenges.insufficientScope(response);
        case BAD_REQUEST -> challenges.badRequest(response);
        case UNAVAILABLE -> challenges.unavailable(response);
        case FORBIDDEN -> challenges.forbidden(response);
      }
    }
  }

  private HttpServletRequest validateExtensionNegotiation(HttpServletRequest request)
      throws IOException {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return request;
    }
    String contentType = request.getContentType();
    if (contentType == null
        || !MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)) {
      throw new McpAdmissionException(McpAdmissionException.Kind.BAD_REQUEST);
    }
    byte[] body = request.getInputStream().readNBytes(properties.maximumRequestBytes() + 1);
    if (body.length == 0 || body.length > properties.maximumRequestBytes()) {
      throw new McpAdmissionException(McpAdmissionException.Kind.BAD_REQUEST);
    }
    JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (RuntimeException invalid) {
      throw new McpAdmissionException(McpAdmissionException.Kind.BAD_REQUEST);
    }
    if (!root.isObject() || !root.path("method").isString()) {
      throw new McpAdmissionException(McpAdmissionException.Kind.BAD_REQUEST);
    }
    if ("initialize".equals(root.path("method").stringValue())) {
      JsonNode extension =
          root.path("params")
              .path("capabilities")
              .path("extensions")
              .path(McpWorkloadProperties.CLIENT_CREDENTIALS_EXTENSION);
      if (!extension.isObject()) {
        throw new McpAdmissionException(McpAdmissionException.Kind.BAD_REQUEST);
      }
    }
    String requestedDomain = requestedDomain(root);
    BufferedRequest buffered = new BufferedRequest(request, body);
    if (requestedDomain != null) {
      buffered.setAttribute(DOMAIN_SCOPE_ATTRIBUTE, requestedDomain);
    }
    return buffered;
  }

  private static String requestedDomain(JsonNode request) {
    String method = request.path("method").stringValue();
    if ("tools/call".equals(method)) {
      JsonNode name = request.path("params").path("name");
      return switch (name.isString() ? name.stringValue() : "") {
        case "files.search" -> "files.read";
        case "calendar.agenda" -> "calendar.read";
        case "calendar.create", "calendar.update", "calendar.delete" -> "calendar.write";
        default -> null;
      };
    }
    JsonNode uri = request.path("params").path("uri");
    if ("resources/read".equals(method) && uri.isString()
        && uri.stringValue().startsWith("weave://files/")) {
      return "files.read";
    }
    return null;
  }

  private static final class BufferedRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private BufferedRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          if (readListener == null) {
            throw new IllegalArgumentException("readListener is required");
          }
        }

        @Override
        public int read() {
          return input.read();
        }

        @Override
        public int read(byte[] target, int offset, int length) {
          return input.read(target, offset, length);
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}
