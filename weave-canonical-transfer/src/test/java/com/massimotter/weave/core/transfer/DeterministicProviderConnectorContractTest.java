package com.massimotter.weave.core.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeterministicProviderConnectorContractTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @ParameterizedTest
    @EnumSource(CanonicalObjectRef.Domain.class)
    void interruptedApplyResumesIdempotentlyWithoutDuplicateCanonicalObjects(
            CanonicalObjectRef.Domain domain) {
        UUID runId = UUID.nameUUIDFromBytes(
                ("run-" + domain.name()).getBytes(StandardCharsets.UTF_8));
        TransferScope scope = new TransferScope("org-1", domain, "workspace-1");
        TransferCheckpoint checkpoint = TransferCheckpoint.initial(runId, domain, NOW);
        DeterministicSource source = new DeterministicSource(domain);
        InterruptingTarget target = new InterruptingTarget(domain);
        CanonicalTransferCoordinator<TestPayload> coordinator =
                new CanonicalTransferCoordinator<>("transfer-v1", modelVersion(domain));

        assertThrows(IllegalStateException.class, () -> coordinator.transferNextPage(
                scope, checkpoint, source, target));

        TransferResult<TestPayload> result = coordinator.transferNextPage(
                scope, checkpoint, source, target);

        assertTrue(result.nextCheckpoint().complete());
        assertEquals(2, source.readCalls());
        assertEquals(2, target.applyCalls());
        assertEquals(6, target.objectCount());
        assertEquals(2, target.idempotencyKeys().size());
        assertEquals(target.idempotencyKeys().get(0), target.idempotencyKeys().get(1));
        assertEquals(result.batch().aggregateSha256(), result.verification().readbackStateSha256());

        Map<CanonicalObjectRef, Set<String>> expectedFields = new HashMap<>();
        for (CanonicalTransferObject<TestPayload> object : result.batch().objects()) {
            expectedFields.put(object.reference(), Set.of("providerField"));
        }
        TransferAccounting.Report accounting = TransferAccounting.verifyExpectedFields(
                expectedFields, result.batch());
        for (FidelityFinding.Classification classification
                : FidelityFinding.Classification.values()) {
            assertEquals(1, accounting.count(classification));
        }
    }

    private static final class DeterministicSource
            implements ProviderSourceConnector<TestPayload> {

        private final CanonicalObjectRef.Domain domain;
        private int readCalls;

        private DeterministicSource(CanonicalObjectRef.Domain domain) {
            this.domain = domain;
        }

        @Override
        public ConnectorDescriptor descriptor() {
            return new ConnectorDescriptor(
                    "provider-a-" + domain.name().toLowerCase(java.util.Locale.ROOT),
                    domain,
                    "provider-a-v1",
                    EnumSet.of(
                            ConnectorDescriptor.Capability.SOURCE_READ,
                            ConnectorDescriptor.Capability.RESUME));
        }

        @Override
        public SourcePage<TestPayload> read(
                TransferScope scope,
                TransferCheckpoint checkpoint) {
            readCalls++;
            if (scope.domain() != domain || checkpoint.domain() != domain) {
                throw new IllegalArgumentException("source domain mismatch");
            }
            if (checkpoint.sequence() != 0) {
                throw new IllegalArgumentException("fixture exposes exactly one page");
            }
            List<CanonicalTransferObject<TestPayload>> objects = fixtureObjects(domain);
            return new SourcePage<>(
                    objects,
                    checkpoint.advance(null, true, NOW.plusSeconds(1)));
        }

        private int readCalls() {
            return readCalls;
        }
    }

    private static final class InterruptingTarget
            implements ProviderTargetConnector<TestPayload> {

        private final CanonicalObjectRef.Domain domain;
        private final Map<String, String> objectDigests = new LinkedHashMap<>();
        private final Map<String, TargetApplyReceipt> receipts = new HashMap<>();
        private final List<String> idempotencyKeys = new ArrayList<>();
        private int applyCalls;
        private boolean injectFailure = true;

        private InterruptingTarget(CanonicalObjectRef.Domain domain) {
            this.domain = domain;
        }

        @Override
        public ConnectorDescriptor descriptor() {
            return new ConnectorDescriptor(
                    "provider-b-" + domain.name().toLowerCase(java.util.Locale.ROOT),
                    domain,
                    "provider-b-v1",
                    EnumSet.of(
                            ConnectorDescriptor.Capability.TARGET_PREFLIGHT,
                            ConnectorDescriptor.Capability.TARGET_APPLY,
                            ConnectorDescriptor.Capability.TARGET_VERIFY,
                            ConnectorDescriptor.Capability.READBACK,
                            ConnectorDescriptor.Capability.RESUME));
        }

        @Override
        public TargetPreflight preflight(CanonicalTransferBatch<TestPayload> batch) {
            if (batch.domain() != domain) {
                return TargetPreflight.rejected("domain-mismatch");
            }
            return TargetPreflight.accepted();
        }

        @Override
        public TargetApplyReceipt apply(
                CanonicalTransferBatch<TestPayload> batch,
                String idempotencyKey) {
            applyCalls++;
            idempotencyKeys.add(idempotencyKey);
            for (CanonicalTransferObject<TestPayload> object : batch.objects()) {
                objectDigests.merge(
                        object.reference().stableKey(),
                        object.payloadSha256(),
                        (current, incoming) -> {
                            if (!current.equals(incoming)) {
                                throw new IllegalStateException("target object conflict");
                            }
                            return current;
                        });
            }

            if (injectFailure) {
                injectFailure = false;
                throw new IllegalStateException("injected failure after target mutation");
            }

            return receipts.computeIfAbsent(idempotencyKey, ignored -> new TargetApplyReceipt(
                    descriptor().connectorKey(),
                    idempotencyKey,
                    "target-batch-" + batch.sequence(),
                    batch.objects().size(),
                    stateSha256()));
        }

        @Override
        public TargetVerification verify(TargetApplyReceipt receipt) {
            String stateSha256 = stateSha256();
            if (!stateSha256.equals(receipt.targetStateSha256())) {
                return new TargetVerification(
                        false,
                        List.of("target-state-changed"),
                        stateSha256);
            }
            return TargetVerification.equivalent(stateSha256);
        }

        private int applyCalls() {
            return applyCalls;
        }

        private int objectCount() {
            return objectDigests.size();
        }

        private List<String> idempotencyKeys() {
            return List.copyOf(idempotencyKeys);
        }

        private String stateSha256() {
            StringBuilder canonical = new StringBuilder();
            new TreeMap<>(objectDigests).forEach((key, value) -> canonical
                    .append(key)
                    .append('\u001f')
                    .append(value)
                    .append('\n'));
            return sha256(canonical.toString());
        }
    }

    private static List<CanonicalTransferObject<TestPayload>> fixtureObjects(
            CanonicalObjectRef.Domain domain) {
        List<FidelityFinding.Classification> classifications = List.of(
                FidelityFinding.Classification.PORTABLE,
                FidelityFinding.Classification.LOSSY,
                FidelityFinding.Classification.UNSUPPORTED,
                FidelityFinding.Classification.MANUAL_REVIEW,
                FidelityFinding.Classification.VENDOR_LOCKED,
                FidelityFinding.Classification.ARCHIVE_ONLY);
        List<CanonicalTransferObject<TestPayload>> objects = new ArrayList<>();
        CanonicalObjectRef firstReference = null;
        for (int index = 0; index < classifications.size(); index++) {
            FidelityFinding.Classification classification = classifications.get(index);
            CanonicalObjectRef reference = new CanonicalObjectRef(
                    "org-1",
                    domain,
                    objectType(domain),
                    domain.name().toLowerCase(java.util.Locale.ROOT) + '-' + (index + 1),
                    modelVersion(domain),
                    1,
                    index == classifications.size() - 2
                            ? CanonicalObjectRef.Lifecycle.TOMBSTONED
                            : CanonicalObjectRef.Lifecycle.ACTIVE);
            if (firstReference == null) {
                firstReference = reference;
            }
            String suffix = classification.name().toLowerCase(java.util.Locale.ROOT);
            ArchiveReference archive = classification == FidelityFinding.Classification.ARCHIVE_ONLY
                    ? new ArchiveReference(
                            "archive-" + suffix,
                            "application/octet-stream",
                            sha256("archive-" + suffix),
                            suffix.length())
                    : null;
            TestPayload payload = new TestPayload("payload-" + domain.name() + '-' + suffix);
            objects.add(new CanonicalTransferObject<>(
                    reference,
                    payload,
                    sha256(payload.body()),
                    Provenance.imported(NOW, "binding-a"),
                    index == 0 ? List.of() : List.of(firstReference),
                    List.of(new FidelityFinding(
                            "providerField",
                            classification,
                            "reason-" + suffix,
                            archive))));
        }
        return List.copyOf(objects);
    }

    private static String objectType(CanonicalObjectRef.Domain domain) {
        return switch (domain) {
            case FILES -> "file";
            case CALENDAR -> "event";
            case CHAT -> "message";
        };
    }

    private static String modelVersion(CanonicalObjectRef.Domain domain) {
        return domain.name().toLowerCase(java.util.Locale.ROOT) + "-v1";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record TestPayload(String body) {
        private TestPayload {
            body = TransferValidation.requireText(body, "body");
        }
    }
}
