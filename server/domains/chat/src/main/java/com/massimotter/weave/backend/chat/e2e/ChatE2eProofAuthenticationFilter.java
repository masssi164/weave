package com.massimotter.weave.backend.chat.e2e;

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

public final class ChatE2eProofAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORITY = "ROLE_CHAT_E2E_PROOF";
    private static final int MAX_AUTHORIZATION_LENGTH = 16_512;

    private final ChatE2eProofSecrets secrets;

    public ChatE2eProofAuthenticationFilter(ChatE2eProofSecrets secrets) {
        this.secrets = secrets;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean queryTokenPresent = request.getParameter("access_token") != null
                || request.getParameter("token") != null;
        if (queryTokenPresent || authorization == null || authorization.length() > MAX_AUTHORIZATION_LENGTH
                || !authorization.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }
        byte[] candidate = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        if (!secrets.matches(candidate)) {
            unauthorized(response);
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "isolated-chat-e2e-proof",
                null,
                List.of(new SimpleGrantedAuthority(AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"chat-e2e-proof-authentication-failed\",\"supportSafe\":true}");
    }
}
