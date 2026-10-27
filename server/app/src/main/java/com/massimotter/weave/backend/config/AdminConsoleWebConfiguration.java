package com.massimotter.weave.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class AdminConsoleWebConfiguration {

    @Component
    static final class AdminConsoleSecurityHeadersFilter extends OncePerRequestFilter {

        private final String contentSecurityPolicy;

        AdminConsoleSecurityHeadersFilter(
                @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer) {
            String oidcOrigin = oidcOrigin(issuer);
            this.contentSecurityPolicy = "default-src 'self'; "
                    + "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                    + "script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'"
                    + (oidcOrigin.isEmpty() ? "" : " " + oidcOrigin);
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            if (AdminConsoleRoutePolicy.isPublicConsoleRequest(request)) {
                response.setHeader("Content-Security-Policy", contentSecurityPolicy);
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            }
            filterChain.doFilter(request, response);
        }

        private static String oidcOrigin(String issuer) {
            if (issuer == null || issuer.isBlank()) {
                return "";
            }
            try {
                URI uri = URI.create(issuer.trim());
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (host == null || !("https".equalsIgnoreCase(scheme)
                        || ("http".equalsIgnoreCase(scheme) && isLoopback(host)))) {
                    return "";
                }
                int port = uri.getPort();
                return scheme.toLowerCase() + "://" + host.toLowerCase()
                        + (port < 0 ? "" : ":" + port);
            } catch (IllegalArgumentException ignored) {
                return "";
            }
        }

        private static boolean isLoopback(String host) {
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        }
    }
}
