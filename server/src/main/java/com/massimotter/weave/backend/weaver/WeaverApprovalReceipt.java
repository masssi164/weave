package com.massimotter.weave.backend.weaver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public record WeaverApprovalReceipt(
        String receiptRef,
        String actorRef,
        String effectiveMemberIssuer,
        String effectiveMemberSubject,
        String organizationRef,
        String workloadClientId,
        List<String> tokenAudiences,
        String delegationRef,
        String entitlementRevision,
        String nonce,
        String runtimeProfileHash,
        String domain,
        String action,
        List<String> scopeRefs,
        String argumentDigest,
        String toolContractVersion,
        String policyVersion,
        String decision,
        String approvalMode,
        String evidenceRef,
        String approvedAt,
        String expiresAt,
        String auditRef) {

    private static final ObjectMapper CANONICAL_JSON =
            new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public WeaverApprovalReceipt {
        receiptRef = safe(receiptRef);
        actorRef = safe(actorRef);
        effectiveMemberIssuer = safe(effectiveMemberIssuer);
        effectiveMemberSubject = safe(effectiveMemberSubject);
        organizationRef = safe(organizationRef);
        workloadClientId = safe(workloadClientId);
        tokenAudiences = List.copyOf(tokenAudiences == null ? List.of() : tokenAudiences);
        delegationRef = safe(delegationRef);
        entitlementRevision = safe(entitlementRevision);
        nonce = safe(nonce);
        runtimeProfileHash = safe(runtimeProfileHash);
        domain = safe(domain);
        action = safe(action);
        scopeRefs = List.copyOf(scopeRefs == null ? List.of() : scopeRefs);
        argumentDigest = safe(argumentDigest);
        toolContractVersion = safe(toolContractVersion);
        policyVersion = safe(policyVersion);
        decision = safe(decision);
        approvalMode = safe(approvalMode);
        evidenceRef = safe(evidenceRef);
        approvedAt = safe(approvedAt);
        expiresAt = safe(expiresAt);
        auditRef = safe(auditRef);
    }

    public WeaverApprovalReceipt(
            String receiptRef,
            String actorRef,
            String runtimeProfileHash,
            String domain,
            String action,
            List<String> scopeRefs,
            String argumentDigest,
            String toolContractVersion,
            String policyVersion,
            String decision,
            String approvalMode,
            String evidenceRef,
            String approvedAt,
            String expiresAt,
            String auditRef) {
        this(
                receiptRef,
                actorRef,
                "https://auth.weave.test/realms/weave",
                "test-member-subject",
                "org:workspace",
                "weave-mcp-server",
                List.of("weave-backend"),
                "delegation://sha256/test",
                "entitlement://runtime-profile/" + safe(runtimeProfileHash),
                "test-nonce",
                runtimeProfileHash,
                domain,
                action,
                scopeRefs,
                argumentDigest,
                toolContractVersion,
                policyVersion,
                decision,
                approvalMode,
                evidenceRef,
                approvedAt,
                expiresAt,
                auditRef);
    }

    public boolean validFor(
            String actorRef,
            String runtimeProfileHash,
            String domain,
            String action,
            List<String> requiredScopeRefs,
            Map<String, Object> arguments,
            String expectedPolicyVersion,
            String expectedToolContractVersion) {
        List<String> safeRequiredScopeRefs = requiredScopeRefs == null
                ? List.of()
                : requiredScopeRefs.stream().distinct().sorted().toList();
        String safeExpectedPolicyVersion = safe(expectedPolicyVersion);
        String safeExpectedToolContractVersion = safe(expectedToolContractVersion);
        return !receiptRef.isBlank()
                && this.actorRef.equals(safe(actorRef))
                && !effectiveMemberIssuer.isBlank()
                && !effectiveMemberSubject.isBlank()
                && !effectiveMemberSubject.startsWith("service-account-")
                && !organizationRef.isBlank()
                && "weave-mcp-server".equals(workloadClientId)
                && tokenAudiences.contains("weave-backend")
                && delegationRef.startsWith("delegation://sha256/")
                && !entitlementRevision.isBlank()
                && !nonce.isBlank()
                && this.runtimeProfileHash.equals(safe(runtimeProfileHash))
                && this.domain.equals(safe(domain))
                && this.action.equals(safe(action))
                && scopeRefs.stream().distinct().sorted().toList().equals(safeRequiredScopeRefs)
                && argumentDigest.equals(argumentDigest(arguments))
                && toolContractVersion.equals(safeExpectedToolContractVersion)
                && policyVersion.equals(safeExpectedPolicyVersion)
                && "approved".equals(decision)
                && List.of("allow-once", "allow-always").contains(approvalMode)
                && evidenceRef.startsWith("elicitation://openclaw/")
                && auditRef.startsWith("audit://")
                && approvedInPast()
                && expiresInFuture();
    }

    public static String argumentDigest(Map<String, Object> arguments) {
        try {
            byte[] canonicalArguments = CANONICAL_JSON.writeValueAsString(arguments == null ? Map.of() : arguments)
                    .getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalArguments));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("tool arguments cannot be canonicalized", exception);
        }
    }

    private boolean approvedInPast() {
        try {
            return !Instant.parse(approvedAt).isAfter(Instant.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean expiresInFuture() {
        try {
            return Instant.parse(expiresAt).isAfter(Instant.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
