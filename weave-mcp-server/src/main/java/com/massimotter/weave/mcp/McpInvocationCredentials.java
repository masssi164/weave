package com.massimotter.weave.mcp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-bound access to the exchanged token.
 *
 * <p>The value is never persisted or rendered and cannot fall back to the inbound MCP bearer.
 */
@RequestScope
@Component
class McpInvocationCredentials {
  private final HttpServletRequest request;

  McpInvocationCredentials(HttpServletRequest request) {
    this.request = request;
  }

  String exchangedBearer() {
    Object value = request.getAttribute(McpRequestAdmissionFilter.EXCHANGED_TOKEN_ATTRIBUTE);
    if (value instanceof ExchangedAccessToken token) {
      return token.value();
    }
    throw new McpAdmissionException(McpAdmissionException.Kind.FORBIDDEN);
  }
}
