package com.massimotter.weave.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Writes Matrix protocol errors for requests stopped before the facade controller. */
final class MatrixClientSecurityErrorWriter {
  private MatrixClientSecurityErrorWriter() {}

  static boolean writeAuthenticationError(
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!isMatrixClientRequest(request)) return false;
    String authorization = request.getHeader("Authorization");
    String code = authorization == null || authorization.isBlank()
        ? "M_MISSING_TOKEN" : "M_UNKNOWN_TOKEN";
    write(response, HttpStatus.UNAUTHORIZED, code,
        "A valid Weave member access token is required.");
    return true;
  }

  static boolean writeAccessDenied(
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!isMatrixClientRequest(request)) return false;
    write(response, HttpStatus.FORBIDDEN, "M_FORBIDDEN",
        "The current member is not authorized for this Matrix resource.");
    return true;
  }

  private static boolean isMatrixClientRequest(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null && path.startsWith("/_matrix/client/");
  }

  private static void write(
      HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader("X-Weave-Projection", "matrix-client-server");
    response.getWriter().write("{\"errcode\":\"" + code + "\",\"error\":\"" + message + "\"}");
  }
}
