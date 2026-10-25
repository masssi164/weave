package com.massimotter.weave.backend.chat.provider.synapse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class MatrixApplicationServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORITY = "ROLE_MATRIX_APPSERVICE";
    private static final int MAX_AUTHORIZATION_LENGTH = 16_512;

    private final MatrixApplicationServiceSecrets secrets;

    public MatrixApplicationServiceAuthenticationFilter(MatrixApplicationServiceSecrets secrets) {
        this.secrets = secrets;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean queryTokenPresent = request.getParameter("access_token") != null;
        if (queryTokenPresent || authorization == null || authorization.length() > MAX_AUTHORIZATION_LENGTH
                || !authorization.startsWith("Bearer ")) {
            forbidden(response);
            return;
        }
        byte[] candidate = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        if (!secrets.matchesHomeserverToken(candidate)) {
            forbidden(response);
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "matrix-homeserver",
                null,
                List.of(new SimpleGrantedAuthority(AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"errcode\":\"M_FORBIDDEN\",\"error\":\"Application Service authentication failed.\"}");
    }
}
