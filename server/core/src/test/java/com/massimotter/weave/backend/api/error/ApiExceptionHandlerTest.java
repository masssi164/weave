package com.massimotter.weave.backend.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

  @Test
  void limitsDiagnosticSourcesToStaticWeaveFramesWithoutMessagesOrFileNames() {
    var failure = new org.springframework.dao.DuplicateKeyException("private SQL and identity");
    failure.setStackTrace(new StackTraceElement[] {
        new StackTraceElement("org.provider.PrivateIdentity", "secret", "private-file", 1),
        new StackTraceElement("com.massimotter.weave.backend.store.Intent", "save", "private-file", 42),
        new StackTraceElement("com.massimotter.weave.backend.store.Intent", "save", "other-file", 42),
        new StackTraceElement("com.massimotter.weave.backend.store.Intent", "private\nvalue", "private-file", 43),
        new StackTraceElement("com.massimotter.weave.backend.service.Invitation", "apply", "private-file", 44),
        new StackTraceElement("com.massimotter.weave.backend.service.Invitation", "reconcile", "private-file", 45),
        new StackTraceElement("com.massimotter.weave.backend.controller.Identity", "reconcile", "private-file", 46),
        new StackTraceElement("com.massimotter.weave.backend.Extra", "extra", "private-file", 47)
    });
    assertThat(ApiExceptionHandler.failureSourceFrames(failure)).isEqualTo(
        "com.massimotter.weave.backend.store.Intent.save:42,"
            + "com.massimotter.weave.backend.service.Invitation.apply:44,"
            + "com.massimotter.weave.backend.service.Invitation.reconcile:45,"
            + "com.massimotter.weave.backend.controller.Identity.reconcile:46");
    assertThat(ApiExceptionHandler.failureCauseTypes(failure))
        .isEqualTo("org.springframework.dao.DuplicateKeyException");
  }

  @Test
  void boundsCyclicCausesAndHandlesMissingProductFrames() {
    var first = new IllegalStateException("private-first");
    var second = new IllegalArgumentException("private-second", first);
    first.initCause(second);
    first.setStackTrace(new StackTraceElement[0]);
    second.setStackTrace(new StackTraceElement[0]);
    assertThat(ApiExceptionHandler.failureCauseTypes(first).split(",")).hasSize(8);
    assertThat(ApiExceptionHandler.failureSourceFrames(first)).isEqualTo("unavailable");
  }

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
            Map.of(
                "providerStatus", 403,
                "providerOperation", "identity-administration"));
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

  @Test
  void preservesNotFoundSemanticsForRetiredRoutes() throws Exception {
    ApiErrorResponseWriter writer = mock(ApiErrorResponseWriter.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    ApiExceptionHandler handler = new ApiExceptionHandler(writer);

    handler.handleNoResource(
        new NoResourceFoundException(
            org.springframework.http.HttpMethod.GET, "/api/v1/workspace/capabilities", ""),
        request,
        response);

    verify(writer)
        .write(
            request,
            response,
            HttpStatus.NOT_FOUND,
            "not-found",
            "The requested resource does not exist.");
  }
}
