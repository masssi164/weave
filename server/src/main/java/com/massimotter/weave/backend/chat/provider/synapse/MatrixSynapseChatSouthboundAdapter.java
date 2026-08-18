package com.massimotter.weave.backend.chat.provider.synapse;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.port.ChatSouthboundProvider;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class MatrixSynapseChatSouthboundAdapter implements ChatSouthboundProvider {

    public static final String PROVIDER_KEY = "matrix-synapse";

    private final SynapseApplicationServiceClient client;
    private final ChatRuntimeProperties.Matrix properties;
    private final Clock clock;
    private final AtomicReference<ReadinessSnapshot> readiness = new AtomicReference<>();
    private final AtomicReference<ProviderBackoff> providerBackoff = new AtomicReference<>();
    private final Object readinessLock = new Object();

    public MatrixSynapseChatSouthboundAdapter(
            ChatRuntimeProperties.Matrix properties,
            MatrixApplicationServiceSecrets secrets,
            ObjectMapper objectMapper,
            Clock clock) {
        this(new SynapseApplicationServiceClient(properties, secrets, objectMapper, clock), properties, clock);
    }

    MatrixSynapseChatSouthboundAdapter(
            SynapseApplicationServiceClient client,
            ChatRuntimeProperties.Matrix properties,
            Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public ProviderReadiness readiness() {
        ReadinessSnapshot current = readiness.get();
        Instant now = clock.instant();
        if (current != null && current.nextProbeAt().isAfter(now)) {
            return current.readiness();
        }
        synchronized (readinessLock) {
            current = readiness.get();
            if (current != null && current.nextProbeAt().isAfter(now)) {
                return current.readiness();
            }
            ProviderReadiness result;
            Instant nextProbeAt;
            int consecutiveFailures;
            ProviderBackoff activeBackoff = activeBackoff(now);
            if (activeBackoff != null) {
                result = ProviderReadiness.degraded(activeBackoff.supportSafeCode());
                nextProbeAt = maximum(
                        now.plus(properties.readinessCacheTtl()),
                        activeBackoff.retryAt());
                readiness.set(new ReadinessSnapshot(
                        result,
                        now,
                        nextProbeAt,
                        activeBackoff.consecutiveFailures()));
                return result;
            }
            try {
                result = client.authenticatedReadiness("_weave_appservice")
                        ? ProviderReadiness.ready("chat-provider-authenticated-capability-ready")
                        : ProviderReadiness.degraded("chat-provider-authenticated-capability-degraded");
                consecutiveFailures = result.available() ? 0 : current == null ? 1 : current.consecutiveFailures() + 1;
                nextProbeAt = now.plus(properties.readinessCacheTtl());
                if (result.available()) {
                    providerBackoff.set(null);
                }
            } catch (SynapseProviderException exception) {
                SynapseProviderException bounded = registerProviderFailure(exception, now);
                result = ProviderReadiness.degraded(bounded.supportSafeCode());
                ProviderBackoff failure = providerBackoff.get();
                consecutiveFailures = failure == null
                        ? current == null ? 1 : current.consecutiveFailures() + 1
                        : failure.consecutiveFailures();
                long backoffSeconds = Math.min(300, 5L << Math.min(consecutiveFailures - 1, 6));
                Instant boundedBackoff = now.plusSeconds(Math.max(
                        properties.readinessCacheTtl().toSeconds(), backoffSeconds));
                nextProbeAt = bounded.retryAt() != null && bounded.retryAt().isAfter(boundedBackoff)
                        ? bounded.retryAt()
                        : boundedBackoff;
            }
            readiness.set(new ReadinessSnapshot(result, now, nextProbeAt, consecutiveFailures));
            return result;
        }
    }

    public SupportSafeReadiness supportSafeReadiness() {
        ProviderReadiness value = readiness();
        ReadinessSnapshot snapshot = readiness.get();
        Instant observedAt = snapshot == null ? clock.instant() : snapshot.observedAt();
        long ageSeconds = Math.max(0, java.time.Duration.between(observedAt, clock.instant()).toSeconds());
        return new SupportSafeReadiness(
                supportSafeState(value),
                value.available(),
                value.supportSafeCode(),
                observedAt,
                ageSeconds,
                snapshot == null ? 0 : snapshot.consecutiveFailures(),
                snapshot == null ? observedAt : snapshot.nextProbeAt());
    }

    @Override
    public ProviderCapabilityProbeResult healthProbe() {
        SupportSafeReadiness observation = supportSafeReadiness();
        Duration retryAfter = observation.available()
                ? null
                : Duration.ofMillis(Math.max(
                        0,
                        Duration.between(clock.instant(), observation.nextProbeAt()).toMillis()));
        return switch (observation.state()) {
            case "available" -> ProviderCapabilityProbeResult.available(observation.supportSafeCode());
            case "unavailable" -> ProviderCapabilityProbeResult.unavailable(observation.supportSafeCode());
            default -> ProviderCapabilityProbeResult.degraded(observation.supportSafeCode(), retryAfter);
        };
    }

    private String supportSafeState(ProviderReadiness readiness) {
        if (readiness.available()) {
            return "available";
        }
        String code = readiness.supportSafeCode();
        return code.contains("unavailable") || code.contains("interrupted")
                ? "unavailable"
                : "degraded";
    }

    @Override
    public ProviderAck ensureVirtualUser(String providerUserRef) {
        return executeProvider(() -> client.ensureVirtualUser(providerUserRef));
    }

    @Override
    public ProviderAck createRoom(
            String providerActorRef,
            String providerAliasIntent,
            String title,
            List<String> invitedProviderActors,
            String initialEncryptionAlgorithm) {
        return executeProvider(() -> client.createRoom(
                providerActorRef,
                providerAliasIntent,
                title,
                invitedProviderActors,
                initialEncryptionAlgorithm));
    }

    @Override
    public ProviderAck joinRoom(String providerActorRef, String providerRoomRef) {
        return executeProvider(() -> client.joinRoom(providerActorRef, providerRoomRef));
    }

    @Override
    public ProviderAck leaveRoom(String providerActorRef, String providerRoomRef) {
        return executeProvider(() -> client.leaveRoom(providerActorRef, providerRoomRef));
    }

    @Override
    public ProviderAck enableEncryption(
            String providerActorRef,
            String providerRoomRef,
            String providerTransactionId,
            String algorithm) {
        return executeProvider(() -> client.enableEncryption(providerActorRef, providerRoomRef, algorithm));
    }

    @Override
    public ProviderAck sendEvent(
            String providerActorRef,
            String providerRoomRef,
            String providerTransactionId,
            ChatEventContent content,
            String providerRelationTargetRef) {
        return executeProvider(() -> client.sendEvent(
                providerActorRef, providerRoomRef, providerTransactionId, content, providerRelationTargetRef));
    }

    @Override
    public ProviderAck redactEvent(
            String providerActorRef,
            String providerRoomRef,
            String providerEventRef,
            String providerTransactionId) {
        return executeProvider(() -> client.redactEvent(
                providerActorRef, providerRoomRef, providerEventRef, providerTransactionId));
    }

    @Override
    public ProviderAck markRead(String providerActorRef, String providerRoomRef, String providerEventRef) {
        return executeProvider(() -> client.markRead(providerActorRef, providerRoomRef, providerEventRef));
    }

    @Override
    public void setTyping(String providerActorRef, String providerRoomRef, boolean typing, int timeoutMilliseconds) {
        executeProvider(() -> {
            client.setTyping(providerActorRef, providerRoomRef, typing, timeoutMilliseconds);
            return null;
        });
    }

    public SupportSafeRoomEvidence readRoomEvidence(
            String providerActorRef,
            String providerRoomRef,
            List<String> expectedJoinedActors,
            List<String> expectedEncryptedEventRefs,
            List<String> expectedCiphertextHashes,
            String outsiderActorRef) {
        SynapseApplicationServiceClient.ProviderRoomEvidence evidence = executeProvider(() -> client.readRoomEvidence(
                providerActorRef,
                providerRoomRef,
                expectedJoinedActors,
                expectedEncryptedEventRefs,
                expectedCiphertextHashes,
                outsiderActorRef));
        return new SupportSafeRoomEvidence(
                evidence.authorizedMembershipExact(),
                evidence.outsiderAbsent(),
                evidence.outsiderReadDenied(),
                evidence.encryptionStateVerified(),
                evidence.encryptedEventRefsExact(),
                evidence.ciphertextHashesExact(),
                evidence.encryptedEventCount(),
                evidence.plaintextEventCount());
    }

    private <T> T executeProvider(Supplier<T> operation) {
        Instant now = clock.instant();
        ProviderBackoff active = activeBackoff(now);
        if (active != null) {
            throw new SynapseProviderException(active.supportSafeCode(), active.retryAt());
        }
        try {
            T result = operation.get();
            providerBackoff.set(null);
            readiness.set(new ReadinessSnapshot(
                    ProviderReadiness.ready("chat-provider-authenticated-operation-ready"),
                    now,
                    now.plus(properties.readinessCacheTtl()),
                    0));
            return result;
        } catch (SynapseProviderException exception) {
            throw registerProviderFailure(exception, now);
        }
    }

    private SynapseProviderException registerProviderFailure(
            SynapseProviderException exception,
            Instant now) {
        if (exception.retryAt() == null) {
            ReadinessSnapshot current = readiness.get();
            int failures = current == null ? 1 : current.consecutiveFailures() + 1;
            readiness.set(new ReadinessSnapshot(
                    ProviderReadiness.degraded(exception.supportSafeCode()),
                    now,
                    now.plus(properties.readinessCacheTtl()),
                    failures));
            return exception;
        }
        ProviderBackoff bounded = providerBackoff.updateAndGet(previous -> {
            int failures = previous == null ? 1 : Math.min(previous.consecutiveFailures() + 1, 30);
            long exponentialSeconds = Math.min(300, 5L << Math.min(failures - 1, 6));
            Instant retryAt = maximum(exception.retryAt(), now.plusSeconds(exponentialSeconds));
            return new ProviderBackoff(exception.supportSafeCode(), retryAt, failures);
        });
        readiness.set(new ReadinessSnapshot(
                ProviderReadiness.degraded(bounded.supportSafeCode()),
                now,
                maximum(now.plus(properties.readinessCacheTtl()), bounded.retryAt()),
                bounded.consecutiveFailures()));
        return new SynapseProviderException(bounded.supportSafeCode(), bounded.retryAt());
    }

    private ProviderBackoff activeBackoff(Instant now) {
        ProviderBackoff value = providerBackoff.get();
        return value != null && value.retryAt().isAfter(now) ? value : null;
    }

    private Instant maximum(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private record ReadinessSnapshot(
            ProviderReadiness readiness,
            Instant observedAt,
            Instant nextProbeAt,
            int consecutiveFailures) {
    }

    private record ProviderBackoff(
            String supportSafeCode,
            Instant retryAt,
            int consecutiveFailures) {
    }

    public record SupportSafeReadiness(
            String state,
            boolean available,
            String supportSafeCode,
            Instant observedAt,
            long observationAgeSeconds,
            int consecutiveFailures,
            Instant nextProbeAt) {
    }

    public record SupportSafeRoomEvidence(
            boolean authorizedMembershipExact,
            boolean outsiderAbsent,
            boolean outsiderReadDenied,
            boolean encryptionStateVerified,
            boolean encryptedEventRefsExact,
            boolean ciphertextHashesExact,
            int encryptedEventCount,
            int plaintextEventCount) {
    }
}
