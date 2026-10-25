package com.massimotter.weave.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".REQUEST_ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    public static String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(ATTRIBUTE);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }

        String incoming = request.getHeader(HEADER);
        if (isSafeRequestId(incoming)) {
            return incoming.trim();
        }

        return UUID.randomUUID().toString();
    }

    private static boolean isSafeRequestId(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 128
                && value.chars().allMatch(character -> Character.isLetterOrDigit(character)
                        || character == '-'
                        || character == '_'
                        || character == '.'
                        || character == ':');
    }
}
