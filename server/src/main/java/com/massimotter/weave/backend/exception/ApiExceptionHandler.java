package com.massimotter.weave.backend.exception;

import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final ApiErrorResponseWriter errorResponseWriter;

    public ApiExceptionHandler(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @ExceptionHandler(ApiErrorException.class)
    public void handleApiError(ApiErrorException exception, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        errorResponseWriter.write(
                request,
                response,
                exception.status(),
                exception.code(),
                exception.getMessage(),
                exception.details());
    }

    @ExceptionHandler(KeycloakAdminException.class)
    public void handleIdentityAdministrationFailure(
            KeycloakAdminException exception,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.BAD_GATEWAY,
                "identity-administration-failed",
                "Identity administration is temporarily unavailable.",
                Map.of("providerStatus", exception.status()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Request validation failed.",
                Map.of("fields", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public void handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Request validation failed.",
                Map.of("violations", exception.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .toList()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleNotReadable(HttpMessageNotReadableException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.BAD_REQUEST,
                "invalid-request-body",
                "Request body could not be parsed.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(
            NoResourceFoundException exception,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.NOT_FOUND,
                "not-found",
                "The requested resource does not exist.");
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(
            Exception exception, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-server-error",
                "The request could not be completed.",
                Map.of("failureCategory", failureCategory(exception)));
    }

    private static String failureCategory(Throwable failure) {
        Throwable current = failure;
        boolean componentStateSeen = false;
        for (int depth = 0; current != null && depth < 12; depth++) {
            String type = current.getClass().getName();
            if (type.startsWith("org.springframework.security.oauth2.")) {
                return "oauth2-client";
            }
            if (type.startsWith("org.springframework.dao.")
                    || type.startsWith("org.hibernate.")
                    || type.startsWith("org.postgresql.")) {
                return "persistence";
            }
            if (type.startsWith("tools.jackson.")) {
                return "provider-projection";
            }
            if (current instanceof IllegalStateException) {
                componentStateSeen = true;
            }
            current = current.getCause();
        }
        return componentStateSeen ? "component-state" : "unexpected";
    }
}
