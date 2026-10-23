package com.massimotter.weave.backend.dogfood;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Closed private process protocol. Only Receipt is suitable for support records. */
public final class DogfoodPreparationProtocol {
    public static final String MARKER = "WEAVE_DOGFOOD_OPERATOR_PREPARATION_RESULT ";
    public static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    static final Set<String> BOUNDARIES = Set.of("none", "invalid-request", "dogfood-posture-required",
            "candidate-mismatch", "configuration-mismatch", "schema-authority-unavailable", "owner-unavailable",
            "owner-policy-denied", "owner-space-access-conflict", "plan-conflict", "operator-reference-conflict",
            "invitation-conflict", "invitation-expired", "invitation-revoked", "invitation-outcome-uncertain",
            "identity-unavailable", "test-member-state-conflict", "test-member-not-activated", "member-proof-required",
            "member-proof-conflict", "space-access-conflict", "space-version-conflict", "identity-provider-unavailable",
            "persistence-unavailable", "audit-unavailable", "operation-incomplete");
    private DogfoodPreparationProtocol() {}

    public record Request(String schemaVersion, String operation, String environment, String nonce,
            String candidateCommit, String sourceTree, String specificationCommit, String apiBaseUrl,
            Plan plan, Receipt previousReceipt, Proof proof) {}
    public record Plan(String schemaVersion, String planId, String nonce, String organizationOrigin,
            String apiOrigin, String issuerUrl, String spaceRef, List<PlannedMember> members) {}
    public record PlannedMember(String slot, String email, String displayName, String role,
            String spaceRole, String invitationIdempotencyKey) {}
    public record Proof(String schemaVersion, String planId, String nonce, List<MemberProof> members) {}
    public record MemberProof(String slot, String accountDigest, String organizationDigest) {}
    public record MemberReceipt(String slot, String intentRef, String invitationHandle, String memberHandle,
            String accountRef, String grantVersion, String intentState, String grantOutcome) {}
    public record Receipt(String schemaVersion, String authorizationKind, String actorKind, String actorRef,
            String operation, String status, String stage, String boundary, String outcome,
            String nonce, String planId, String environment, String candidateCommit, String sourceTree,
            String specificationCommit, String organizationOrigin, String apiBaseUrl, String issuerUrl,
            String organizationId, String spaceRef, String intendedOwnerAccountRef, String intendedOwnerMemberHandle,
            List<MemberReceipt> members, boolean supportSafe, boolean containsSecretValues) {}
    public record PrivateData(String ownerDisplayName) {}
    public record Envelope(String schemaVersion, Receipt receipt, PrivateData privateData) {}

    public static Request read(InputStream input) throws Exception {
        byte[] bytes = input.readNBytes(65_537);
        require(bytes.length > 0 && bytes.length <= 65_536, "invalid-request");
        // A strict UTF-8 decoder rejects invalid byte sequences instead of replacing them.
        String value = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        var tree = JSON.readTree(value);
        require(tree != null && tree.isObject() && tree.size() == 11
                && tree.has("previousReceipt") && tree.has("proof"), "invalid-request");
        fields(tree, Request.class);
        fields(tree.path("plan"), Plan.class);
        for (var member : tree.path("plan").path("members")) fields(member, PlannedMember.class);
        if (!tree.path("proof").isNull()) {
            fields(tree.path("proof"), Proof.class);
            for (var member : tree.path("proof").path("members")) fields(member, MemberProof.class);
        }
        if (!tree.path("previousReceipt").isNull()) {
            fields(tree.path("previousReceipt"), Receipt.class);
            for (var member : tree.path("previousReceipt").path("members")) fields(member, MemberReceipt.class);
        }
        Request request = JSON.treeToValue(tree, Request.class);
        validate(request);
        return request;
    }

    private static void fields(tools.jackson.databind.JsonNode node, Class<?> type) {
        require(node.isObject() && node.size() == type.getRecordComponents().length, "invalid-request");
        for (var field : type.getRecordComponents()) require(node.has(field.getName()), "invalid-request");
    }

    static void validate(Request r) {
        require(r != null && "weave.dogfood-operator-preparation-request/v1".equals(r.schemaVersion())
                && Set.of("invitations", "grants").contains(nullSafe(r.operation()))
                && "dogfood".equals(r.environment()) && hex(r.nonce(), 32)
                && hex(r.candidateCommit(), 40) && hex(r.sourceTree(), 40) && hex(r.specificationCommit(), 40), "invalid-request");
        Plan p = r.plan();
        require(p != null && "weave.dogfood-admin-preparation-plan/v1".equals(p.schemaVersion())
                && hex(p.planId(), 32) && r.nonce().equals(p.nonce())
                && "workspace-default".equals(p.spaceRef()) && p.members() != null && p.members().size() == 2,
                "invalid-request");
        URI org = https(p.organizationOrigin());
        require(org.getRawPath().isEmpty(), "invalid-request");
        URI api = https(r.apiBaseUrl());
        require(api.getRawPath().equals("/api") && https(p.apiOrigin()).getRawPath().isEmpty()
                && (api.getScheme() + "://" + api.getRawAuthority()).equals(p.apiOrigin()), "invalid-request");
        https(p.issuerUrl());
        for (int index = 0; index < 2; index++) {
            var member = p.members().get(index);
            String slot = index == 0 ? "A" : "B";
            require(member != null && slot.equals(member.slot()) && "member".equals(member.role())
                    && "EDIT".equals(member.spaceRole())
                    && ("weave-smoke-" + p.planId() + "-" + slot.toLowerCase(java.util.Locale.ROOT) + "@" + org.getHost()).equals(member.email())
                    && ("Weave test member " + slot + " " + p.planId().substring(0, 8)).equals(member.displayName())
                    && ("dogfood-" + p.planId() + "-" + slot).equals(member.invitationIdempotencyKey()), "plan-conflict");
        }
        if ("invitations".equals(r.operation())) require(r.proof() == null, "invalid-request");
        else {
            Proof proof = r.proof();
            require(proof != null && "weave.dogfood-admin-preparation-proof/v1".equals(proof.schemaVersion())
                    && p.planId().equals(proof.planId()) && r.nonce().equals(proof.nonce())
                    && proof.members() != null && proof.members().size() == 2, "member-proof-required");
            for (int i = 0; i < 2; i++) {
                var item = proof.members().get(i);
                require(item != null && p.members().get(i).slot().equals(item.slot()) && hex(item.accountDigest(), 64)
                        && hex(item.organizationDigest(), 64), "member-proof-conflict");
            }
            require(!proof.members().get(0).accountDigest().equals(proof.members().get(1).accountDigest()), "member-proof-conflict");
        }
        if (r.previousReceipt() != null) validatePrevious(r);
        if ("grants".equals(r.operation())) {
            Receipt previous = r.previousReceipt();
            require(previous != null && "passed".equals(previous.status()) && "none".equals(previous.boundary())
                    && (("invitations".equals(previous.operation()) && "invitations-ready".equals(previous.stage()))
                    || ("grants".equals(previous.operation()) && "grants-ready".equals(previous.stage())))
                    && previous.organizationId() != null && !previous.organizationId().isBlank()
                    && previous.intendedOwnerAccountRef() != null && previous.intendedOwnerMemberHandle() != null
                    && previous.members().stream().allMatch(member -> member.intentRef() != null && member.invitationHandle() != null),
                    "operator-reference-conflict");
        }
    }

    private static void validatePrevious(Request r) {
        Receipt p = r.previousReceipt();
        require("weave.dogfood-operator-preparation-receipt/v1".equals(p.schemaVersion())
                && "dogfood-operator".equals(p.authorizationKind()) && "dogfood-operator".equals(p.actorKind())
                && actor(r).equals(p.actorRef()) && r.plan().planId().equals(p.planId()) && "dogfood".equals(p.environment())
                && r.plan().organizationOrigin().equals(p.organizationOrigin()) && r.apiBaseUrl().equals(p.apiBaseUrl())
                && r.plan().issuerUrl().equals(p.issuerUrl()) && "workspace-default".equals(p.spaceRef())
                && p.supportSafe() && !p.containsSecretValues() && hex(p.nonce(), 32)
                && hex(p.candidateCommit(), 40) && hex(p.sourceTree(), 40) && hex(p.specificationCommit(), 40)
                && Set.of("invitations", "grants").contains(nullSafe(p.operation()))
                && Set.of("passed", "blocked", "incomplete").contains(nullSafe(p.status()))
                && Set.of("invitations-ready", "grants-ready", "incomplete").contains(nullSafe(p.stage()))
                && BOUNDARIES.contains(nullSafe(p.boundary()))
                && Set.of("CREATED", "VERIFIED", "PARTIAL", "BLOCKED").contains(nullSafe(p.outcome()))
                && p.members() != null && p.members().size() == 2, "operator-reference-conflict");
        for (int i = 0; i < 2; i++) {
            var m = p.members().get(i);
            require(m != null && r.plan().members().get(i).slot().equals(m.slot())
                    && nullablePattern(m.intentRef(), "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    && nullablePattern(m.invitationHandle(), "inv_[A-Za-z0-9_-]{32}")
                    && nullablePattern(m.memberHandle(), "mem_[A-Za-z0-9_-]{32}")
                    && nullablePattern(m.accountRef(), "acct_[0-9a-f]{32}")
                    && nullablePattern(m.grantVersion(), "\\\"sm-[0-9]+\\\"")
                    && Set.of("NOT_ISSUED", "PENDING", "APPLIED", "FAILED", "EXPIRED", "UNAVAILABLE").contains(nullSafe(m.intentState()))
                    && Set.of("NOT_REQUESTED", "CREATED", "VERIFIED", "BLOCKED").contains(nullSafe(m.grantOutcome())), "operator-reference-conflict");
        }
        require(nullablePattern(p.intendedOwnerAccountRef(), "acct_[0-9a-f]{32}")
                && nullablePattern(p.intendedOwnerMemberHandle(), "mem_[A-Za-z0-9_-]{32}"), "operator-reference-conflict");
        if ("passed".equals(p.status())) {
            require("none".equals(p.boundary()) && Set.of("CREATED", "VERIFIED").contains(p.outcome())
                    && p.organizationId() != null && !p.organizationId().isBlank()
                    && p.intendedOwnerAccountRef() != null && p.intendedOwnerMemberHandle() != null,
                    "operator-reference-conflict");
            if ("invitations-ready".equals(p.stage())) {
                require("invitations".equals(p.operation()) && p.members().stream().allMatch(member ->
                        member.intentRef() != null && member.invitationHandle() != null && member.grantVersion() == null
                        && "NOT_REQUESTED".equals(member.grantOutcome())
                        && (("PENDING".equals(member.intentState()) && member.memberHandle() == null && member.accountRef() == null)
                        || ("APPLIED".equals(member.intentState()) && member.memberHandle() != null && member.accountRef() != null))),
                        "operator-reference-conflict");
            } else {
                require("grants-ready".equals(p.stage()) && "grants".equals(p.operation())
                        && p.members().stream().allMatch(member -> member.intentRef() != null && member.invitationHandle() != null
                        && member.memberHandle() != null && member.accountRef() != null && member.grantVersion() != null
                        && "APPLIED".equals(member.intentState()) && Set.of("CREATED", "VERIFIED").contains(member.grantOutcome())),
                        "operator-reference-conflict");
            }
        } else {
            require("incomplete".equals(p.stage()) && !"none".equals(p.boundary())
                    && Set.of("PARTIAL", "BLOCKED").contains(p.outcome()), "operator-reference-conflict");
        }
    }

    static URI https(String value) {
        try {
            URI uri = URI.create(value);
            require("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && !value.endsWith("/"), "invalid-request");
            return uri;
        } catch (RuntimeException failure) { throw new Blocked("invalid-request"); }
    }
    static String actor(Request r) { return "operator:dogfood-prepare:" + r.plan().planId(); }
    static String digest(String kind, String issuer, String reference) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    JSON.writeValueAsString(List.of(kind, issuer, reference)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new Blocked("operation-incomplete"); }
    }
    static void require(boolean condition, String boundary) { if (!condition) throw new Blocked(boundary); }
    static boolean hex(String value, int length) { return value != null && value.matches("[0-9a-f]{" + length + "}"); }
    private static boolean nullablePattern(String value, String pattern) { return value == null || value.matches(pattern); }
    private static String nullSafe(String value) { return value == null ? "" : value; }
    static final class Blocked extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String boundary;
        Blocked(String boundary) { super(boundary); this.boundary = boundary; }
    }
}
