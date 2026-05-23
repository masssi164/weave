package com.massimotter.weave.backend.exception;

import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
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
}
