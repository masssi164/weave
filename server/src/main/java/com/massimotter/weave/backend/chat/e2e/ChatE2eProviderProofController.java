package com.massimotter.weave.backend.chat.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.provider.synapse.ChatProviderPortAdapterResolver;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import com.massimotter.weave.backend.config.ChatE2eProofSecurityConfiguration;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@ConditionalOnProperty(name = "weave.chat.e2e-proof.enabled", havingValue = "true")
public final class ChatE2eProviderProofController {

    private static final int MAX_BODY_BYTES = 32_768;
    private static final Set<String> REQUEST_FIELDS = Set.of(
            "runId", "tenantId", "conversationId", "author", "collaborator", "outsider",
            "eventCorrelationSha256");
    private static final Set<String> IDENTITY_FIELDS = Set.of("identityIssuer", "actorRef");

    private final CanonicalChatStore store;
    private final SynapseBackedCanonicalChatAdapter adapter;
    private final MatrixSynapseChatSouthboundAdapter provider;
    private final ChatE2eProofProperties properties;
    private final ChatE2eProofSecrets secrets;
    private final ObjectMapper objectMapper;

    public ChatE2eProviderProofController(
            CanonicalChatStore store,
            ChatProviderPortAdapterResolver adapterResolver,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatE2eProofProperties properties,
            ChatE2eProofSecrets secrets,
            ObjectMapper objectMapper) {
        this.store = store;
        this.adapter = adapterResolver.synapseAdapter();
        this.provider = provider;
        this.properties = properties;
        this.secrets = secrets;
        this.objectMapper = objectMapper;
    }

    @PostMapping(ChatE2eProofSecurityConfiguration.PATH)
    public ResponseEntity<?> proof(HttpServletRequest request) {
        try {
            ProofRequest input = parseRequest(boundedBody(request));
            requireExactRun(input.runId());
            String tenant = required(input.tenantId(), "tenant", 160);
            ConversationId conversation = new ConversationId(required(
                    input.conversationId(), "conversation", 160));
            ProofIdentity author = requireIdentity(input.author());
            ProofIdentity collaborator = requireIdentity(input.collaborator());
            ProofIdentity outsider = requireIdentity(input.outsider());
            List<String> eventCorrelationHashes = requireCorrelationHashes(input.eventCorrelationSha256());
            CanonicalChatStore.ProviderMapping room = store.mapping(
                            tenant, provider.providerKey(), "conversation", conversation.value())
                    .filter(mapping -> "acknowledged".equals(mapping.state()))
                    .filter(mapping -> mapping.providerRef() != null && !mapping.providerRef().isBlank())
                    .orElseThrow(() -> new IllegalArgumentException("proof room mapping is unavailable"));
            String contextId = store.contextId(tenant, conversation)
                    .orElseThrow(() -> new IllegalArgumentException("proof context binding is unavailable"));
            ChatRequestContext authorContext = context(tenant, contextId, author);
            ChatRequestContext collaboratorContext = context(tenant, contextId, collaborator);
            ChatRequestContext outsiderContext = context(tenant, contextId, outsider);

            CanonicalChatStore.ProviderMapping authorMapping = adapter.actorMapping(authorContext);
            CanonicalChatStore.ProviderMapping collaboratorMapping = adapter.actorMapping(collaboratorContext);
            var outsiderMapping = adapter.actorMappingIfPresent(outsiderContext);

            MatrixSynapseChatSouthboundAdapter.SupportSafeRoomEvidence providerEvidence = provider.readRoomEvidence(
                    authorMapping.providerRef(),
                    room.providerRef(),
                    List.of(authorMapping.providerRef(), collaboratorMapping.providerRef()),
                    store.acknowledgedProviderEventRefs(tenant, conversation, provider.providerKey()),
                    eventCorrelationHashes,
                    outsiderMapping.map(CanonicalChatStore.ProviderMapping::providerRef).orElse(null));
            CanonicalChatStore.EvidenceSnapshot canonical = store.evidence(
                    tenant, conversation, provider.providerKey());
            MatrixSynapseChatSouthboundAdapter.SupportSafeReadiness readiness = provider.supportSafeReadiness();
            boolean authorJoined = joined(authorContext, conversation);
            boolean collaboratorJoined = joined(collaboratorContext, conversation);
            boolean outsiderJoined = joined(outsiderContext, conversation);
            List<IdentityProof> identities = List.of(
                    new IdentityProof("author", hmac(identityMaterial(author)), true, authorJoined,
                            providerEvidence.authorizedMembershipExact(), false),
                    new IdentityProof("collaborator", hmac(identityMaterial(collaborator)), true, collaboratorJoined,
                            providerEvidence.authorizedMembershipExact(), false),
                    new IdentityProof("outsider", hmac(identityMaterial(outsider)), outsiderMapping.isPresent(), outsiderJoined,
                            outsiderMapping.isPresent() && !providerEvidence.outsiderAbsent(),
                            providerEvidence.outsiderReadDenied()));
            return ResponseEntity.ok(new ProofResponse(
                    "chat-provider-proof-v1",
                    hmac(tenant + "\u0000" + conversation.value() + "\u0000" + input.runId()),
                    hmac(input.runId()),
                    adapter.configured(),
                    canonical.persistencePosture(),
                    readiness.state(),
                    readiness.available(),
                    readiness.supportSafeCode(),
                    readiness.observationAgeSeconds(),
                    readiness.consecutiveFailures(),
                    readiness.nextProbeAt(),
                    identities,
                    providerEvidence.authorizedMembershipExact(),
                    providerEvidence.outsiderAbsent(),
                    providerEvidence.outsiderReadDenied(),
                    providerEvidence.encryptionStateVerified(),
                    providerEvidence.encryptedEventRefsExact(),
                    providerEvidence.ciphertextHashesExact(),
                    canonical.canonicalConversationCount(),
                    canonical.canonicalJoinedMemberCount(),
                    canonical.canonicalCommittedEventCount(),
                    canonical.canonicalEncryptedEventCount(),
                    canonical.canonicalPlaintextEventCount(),
                    providerEvidence.encryptedEventCount(),
                    providerEvidence.plaintextEventCount(),
                    canonical.pendingOperationCount(),
                    canonical.failedOperationCount(),
                    canonical.committedOperationCount(),
                    canonical.bridgeLedgerCount(),
                    canonical.callbackTransactionCount(),
                    canonical.callbackDuplicateCount(),
                    canonical.callbackSemanticMismatchCount(),
                    canonical.quarantineCount(),
                    canonical.degradedMappingCount(),
                    canonical.observedAt(),
                    true));
        } catch (IllegalArgumentException | IOException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "chat-provider-proof-request-invalid",
                    "supportSafe", true));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", "chat-provider-proof-unavailable",
                    "supportSafe", true));
        }
    }

    private ProofRequest parseRequest(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject() || !fieldNames(root).equals(REQUEST_FIELDS)) {
            throw new IllegalArgumentException("proof request shape is invalid");
        }
        for (String identity : List.of("author", "collaborator", "outsider")) {
            JsonNode value = root.path(identity);
            if (!value.isObject() || !fieldNames(value).equals(IDENTITY_FIELDS)) {
                throw new IllegalArgumentException("proof identity shape is invalid");
            }
        }
        return objectMapper.treeToValue(root, ProofRequest.class);
    }

    private Set<String> fieldNames(JsonNode value) {
        java.util.Set<String> names = new java.util.HashSet<>();
        value.propertyNames().forEach(names::add);
        return Set.copyOf(names);
    }

    private byte[] boundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new IOException("proof request body is too large");
        }
        try (InputStream input = request.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("proof request body is too large");
            }
            return bytes;
        }
    }

    private boolean joined(ChatRequestContext context, ConversationId conversation) {
        try {
            store.conversation(context, conversation);
            return true;
        } catch (ChatAccessDeniedException | IllegalArgumentException exception) {
            return false;
        }
    }

    private ChatRequestContext context(String tenant, String contextId, ProofIdentity identity) {
        return new ChatRequestContext(
                tenant, contextId, identity.identityIssuer(), new ChatActorRef(identity.actorRef()));
    }

    private ProofIdentity requireIdentity(ProofIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("proof identity is missing");
        }
        String issuer = required(identity.identityIssuer(), "identity issuer", 512);
        String actor = required(identity.actorRef(), "actor reference", 255);
        if (!actor.matches("user:[A-Za-z0-9._:@/-]{1,240}")) {
            throw new IllegalArgumentException("proof actor reference is invalid");
        }
        return new ProofIdentity(issuer, actor);
    }

    static List<String> requireCorrelationHashes(List<String> hashes) {
        if (hashes == null || hashes.size() < 2 || hashes.size() > 3
                || hashes.stream().distinct().count() != hashes.size()
                || hashes.stream().anyMatch(value -> value == null || !value.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("proof event correlations are invalid");
        }
        return List.copyOf(hashes);
    }

    private void requireExactRun(String runId) {
        byte[] expected = properties.requiredRunId().getBytes(StandardCharsets.UTF_8);
        byte[] actual = runId == null ? new byte[0] : runId.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("proof run is invalid");
        }
    }

    private String required(String value, String label, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value.trim();
    }

    private String identityMaterial(ProofIdentity identity) {
        return identity.identityIssuer() + "\u0000" + identity.actorRef();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secrets.hmacKey(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Chat E2E proof hashing is unavailable.", exception);
        }
    }

    public record ProofRequest(
            String runId,
            String tenantId,
            String conversationId,
            ProofIdentity author,
            ProofIdentity collaborator,
            ProofIdentity outsider,
            List<String> eventCorrelationSha256) {
    }

    public record ProofIdentity(String identityIssuer, String actorRef) {
    }

    public record IdentityProof(
            String role,
            String identityHash,
            boolean providerMapped,
            boolean canonicalJoined,
            boolean providerJoined,
            boolean providerReadDenied) {
    }

    public record ProofResponse(
            String contractVersion,
            String correlationHash,
            String runIdHash,
            boolean adapterConfigured,
            String canonicalStorage,
            String providerCapabilityState,
            boolean providerCapabilityAvailable,
            String providerCapabilityCode,
            long providerObservationAgeSeconds,
            int providerConsecutiveFailures,
            Instant providerBackoffUntil,
            List<IdentityProof> identities,
            boolean providerMembershipExact,
            boolean outsiderAbsent,
            boolean outsiderReadDenied,
            boolean providerEncryptionStateVerified,
            boolean providerEventMappingExact,
            boolean providerCiphertextCorrelationExact,
            long canonicalConversationCount,
            long canonicalJoinedMemberCount,
            long canonicalCommittedEventCount,
            long canonicalEncryptedEventCount,
            long canonicalPlaintextEventCount,
            long providerEncryptedEventCount,
            long providerPlaintextEventCount,
            long pendingOperationCount,
            long failedOperationCount,
            long committedOperationCount,
            long bridgeLedgerCount,
            long callbackTransactionCount,
            long callbackDuplicateCount,
            long callbackSemanticMismatchCount,
            long quarantineCount,
            long degradedOperationCount,
            Instant observedAt,
            boolean supportSafe) {
    }
}
