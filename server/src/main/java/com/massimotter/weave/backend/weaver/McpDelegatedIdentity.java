package com.massimotter.weave.backend.weaver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/** Immutable support-safe identity projection for a member token exchanged by the MCP workload. */
public record McpDelegatedIdentity(
        String issuer,
        String memberSubject,
        String organizationRef,
        String workloadClientId,
        List<String> audiences,
        String delegationRef) {

    public McpDelegatedIdentity {
        issuer = safe(issuer);
        memberSubject = safe(memberSubject);
        organizationRef = safe(organizationRef);
        workloadClientId = safe(workloadClientId);
        audiences = List.copyOf(audiences == null ? List.of() : audiences);
        delegationRef = safe(delegationRef);
    }

    public static McpDelegatedIdentity require(Jwt jwt, String expectedWorkloadClientId, String expectedAudience) {
        String subject = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String azp = jwt.getClaimAsString("azp");
        String clientId = jwt.getClaimAsString("client_id");
        if (!StringUtils.hasText(subject)
                || subject.startsWith("service-account-")
                || (username != null && username.startsWith("service-account-"))
                || !expectedWorkloadClientId.equals(azp)
                || (clientId != null && !expectedWorkloadClientId.equals(clientId))
                || !jwt.getAudience().contains(expectedAudience)) {
            throw new AccessDeniedException("A delegated member token from the MCP workload is required.");
        }
        String issuer = jwt.getIssuer() == null ? jwt.getClaimAsString("iss") : jwt.getIssuer().toString();
        if (!StringUtils.hasText(issuer)) {
            throw new AccessDeniedException("The delegated member token issuer is required.");
        }
        String organization = jwt.getClaimAsString("weave_tenant_id");
        if (!StringUtils.hasText(organization)) {
            throw new AccessDeniedException("The delegated member token organization is required.");
        }
        String organizationRef = "org:" + organization;
        String correlation = jwt.getId();
        if (!StringUtils.hasText(correlation)) {
            throw new AccessDeniedException("The delegated member token correlation ID is required.");
        }
        return new McpDelegatedIdentity(
                issuer,
                subject,
                organizationRef,
                expectedWorkloadClientId,
                jwt.getAudience().stream().sorted().toList(),
                "delegation://sha256/" + digest(issuer + "|" + subject + "|" + expectedWorkloadClientId + "|" + correlation));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
