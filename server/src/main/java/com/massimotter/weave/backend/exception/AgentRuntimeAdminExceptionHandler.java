package com.massimotter.weave.backend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectoryException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonNotFoundException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import com.massimotter.weave.backend.config.AgentRuntimeErrorResponseWriter;
import com.massimotter.weave.backend.controller.AgentRuntimeAdminController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the ARC bounded context to its closed error contract without provider-detail leakage. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AgentRuntimeAdminController.class)
public final class AgentRuntimeAdminExceptionHandler {
    private final AgentRuntimeErrorResponseWriter errors;

    public AgentRuntimeAdminExceptionHandler(
            ObjectProvider<AgentRuntimeErrorResponseWriter> providedErrors,
            ObjectMapper objectMapper) {
        this.errors = providedErrors.getIfAvailable(
                () -> new AgentRuntimeErrorResponseWriter(objectMapper));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            BindException.class,
            IllegalArgumentException.class
    })
    public void invalidRequest(Exception failure, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        errors.write(request, response, HttpStatus.BAD_REQUEST,
                "agent-runtime-invalid-request", "unavailable", false,
                "The Agent Runtime request is invalid.");
    }

    @ExceptionHandler(RuntimePersonNotFoundException.class)
    public void notFound(RuntimePersonNotFoundException failure, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        errors.write(request, response, HttpStatus.NOT_FOUND,
                "agent-runtime-not-found", "not_configured", false,
                "The requested Agent Runtime is unavailable.");
    }

    @ExceptionHandler({RuntimeCommandConflictException.class, StaleRuntimeCellException.class})
    public void conflict(RuntimeException failure, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        errors.write(request, response, HttpStatus.CONFLICT,
                "agent-runtime-conflict", "degraded", false,
                "The Agent Runtime request conflicts with its current authoritative state.");
    }

    @ExceptionHandler(RuntimeEntitlementDeniedException.class)
    public void notEntitled(RuntimeEntitlementDeniedException failure, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        errors.write(request, response, HttpStatus.CONFLICT,
                "agent-runtime-not-entitled", "not_entitled", false,
                "The requested person is not currently entitled to an Agent Runtime.");
    }

    @ExceptionHandler({
            RuntimeEntitlementAuthorityException.class,
            RuntimePersonDirectoryException.class,
            RuntimePolicyException.class,
            RuntimeProfileSigningKeyException.class,
            RuntimeStateStoreException.class,
            RuntimeWorkloadIdentityException.class,
            InvalidRuntimeProfileException.class,
            DataAccessException.class
    })
    public void unavailable(RuntimeException failure, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                "agent-runtime-dependency-unavailable", "unavailable", true,
                "Agent Runtime administration is temporarily unavailable.");
    }

    @ExceptionHandler(ApiErrorException.class)
    public void identityError(ApiErrorException failure, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        HttpStatus status = failure.status().is4xxClientError() ? failure.status() : HttpStatus.BAD_REQUEST;
        errors.write(request, response, status,
                "agent-runtime-invalid-identity", "unavailable", false,
                "The authenticated administrator identity is invalid.");
    }

    @ExceptionHandler(Exception.class)
    public void unexpected(Exception failure, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                "agent-runtime-unavailable", "unavailable", true,
                "Agent Runtime administration is temporarily unavailable.");
    }
}
