package com.massimotter.weave.backend.exception;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class ApiExceptionHandlerTest {

  @Test
  void mapsIdentityProviderFailuresWithoutProviderPayloads() throws Exception {
    ApiErrorResponseWriter writer = mock(ApiErrorResponseWriter.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    ApiExceptionHandler handler = new ApiExceptionHandler(writer);

    handler.handleIdentityAdministrationFailure(
        new KeycloakAdminException(403, "provider payload must stay private"), request, response);

    verify(writer)
        .write(
            request,
            response,
            HttpStatus.BAD_GATEWAY,
            "identity-administration-failed",
            "Identity administration is temporarily unavailable.",
            Map.of("providerStatus", 403));
  }

  @Test
  void categorizesUnexpectedOAuthFailuresWithoutLeakingExceptionDetails() throws Exception {
    ApiErrorResponseWriter writer = mock(ApiErrorResponseWriter.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    ApiExceptionHandler handler = new ApiExceptionHandler(writer);

    handler.handleUnexpected(
        new IllegalStateException(
            "must-not-leak",
            new OAuth2AuthorizationException(
                new OAuth2Error("invalid_client"), "provider detail must stay private")),
        request,
        response);

    verify(writer)
        .write(
            request,
            response,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-server-error",
            "The request could not be completed.",
            Map.of("failureCategory", "oauth2-client"));
  }
}
