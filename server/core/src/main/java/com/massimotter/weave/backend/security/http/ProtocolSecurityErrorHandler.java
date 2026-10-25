package com.massimotter.weave.backend.security.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** A serving protocol's denial envelope; shared token validation and authority stay unchanged. */
public interface ProtocolSecurityErrorHandler {
    boolean writeAuthenticationError(HttpServletRequest request, HttpServletResponse response) throws IOException;
    boolean writeAccessDenied(HttpServletRequest request, HttpServletResponse response) throws IOException;
}
