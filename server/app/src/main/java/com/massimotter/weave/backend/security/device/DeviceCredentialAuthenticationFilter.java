package com.massimotter.weave.backend.security.device;

import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DeviceCredentialAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceCredentialService credentialService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public DeviceCredentialAuthenticationFilter(
            ObjectProvider<DeviceCredentialService> credentialServiceProvider,
            ApiErrorResponseWriter errorResponseWriter) {
        this.credentialService = credentialServiceProvider.getIfAvailable();
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String domain = davDomain(request.getRequestURI());
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (domain == null
                || credentialService == null
                || authorization == null
                || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            BasicCredentials basic = decode(authorization.substring(6));
            DeviceCredential credential = credentialService.authenticate(domain, basic.username(), basic.secret());
            Jwt jwt = Jwt.withTokenValue("device:" + credential.credentialId())
                    .header("alg", "weave-device-credential")
                    .subject(credential.subject())
                    .issuedAt(credential.issuedAt())
                    .expiresAt(credential.expiresAt())
                    .claim("preferred_username", credential.username())
                    .claim("weave_tenant_id", credential.tenantId())
                    .claim("tenant_id", credential.tenantId())
                    .claim("weave_auth_method", "device_credential")
                    .claim("weave_credential_id", credential.credentialId())
                    .claim("weave_capabilities", credential.capabilities().stream().sorted().toList())
                    .build();
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    jwt,
                    List.of(new SimpleGrantedAuthority("SCOPE_weave:workspace")),
                    credential.username());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Weave DAV\", charset=\"UTF-8\"");
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "device-credential-invalid",
                    "The Weave DAV device credential is invalid, expired, or revoked.");
        }
    }

    private String davDomain(String path) {
        if (path != null && (path.equals("/dav/files") || path.startsWith("/dav/files/"))) {
            return "files";
        }
        if (path != null && (path.equals("/caldav") || path.startsWith("/caldav/"))) {
            return "calendar";
        }
        return null;
    }

    private BasicCredentials decode(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 1 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException("invalid basic credential");
            }
            return new BasicCredentials(decoded.substring(0, separator), decoded.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new DeviceCredentialException(DeviceCredentialException.Reason.INVALID);
        }
    }

    private record BasicCredentials(String username, String secret) {
    }
}
