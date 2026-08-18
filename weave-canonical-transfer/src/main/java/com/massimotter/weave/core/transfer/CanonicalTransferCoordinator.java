package com.massimotter.weave.core.transfer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Framework-free orchestration of one resumable source-to-target transfer page. */
public final class CanonicalTransferCoordinator<T> {

    private final String transferFormatVersion;
    private final String canonicalModelVersion;

    public CanonicalTransferCoordinator(
            String transferFormatVersion,
            String canonicalModelVersion) {
        this.transferFormatVersion = TransferValidation.requireText(
                transferFormatVersion, "transferFormatVersion");
        this.canonicalModelVersion = TransferValidation.requireText(
                canonicalModelVersion, "canonicalModelVersion");
    }

    public TransferResult<T> transferNextPage(
            TransferScope scope,
            TransferCheckpoint currentCheckpoint,
            ProviderSourceConnector<T> source,
            ProviderTargetConnector<T> target) {
        TransferValidation.require(scope, "scope");
        TransferValidation.require(currentCheckpoint, "currentCheckpoint");
        TransferValidation.require(source, "source");
        TransferValidation.require(target, "target");

        if (currentCheckpoint.complete()) {
            throw new IllegalStateException("a completed transfer cannot read another source page");
        }
        requireDomain(scope.domain(), currentCheckpoint.domain(), "checkpoint");

        ConnectorDescriptor sourceDescriptor = TransferValidation.require(
                source.descriptor(), "source descriptor");
        ConnectorDescriptor targetDescriptor = TransferValidation.require(
                target.descriptor(), "target descriptor");
        requireDomain(scope.domain(), sourceDescriptor.domain(), "source connector");
        requireDomain(scope.domain(), targetDescriptor.domain(), "target connector");
        requireCapability(sourceDescriptor, ConnectorDescriptor.Capability.SOURCE_READ);
        requireCapability(targetDescriptor, ConnectorDescriptor.Capability.TARGET_PREFLIGHT);
        requireCapability(targetDescriptor, ConnectorDescriptor.Capability.TARGET_APPLY);
        requireCapability(targetDescriptor, ConnectorDescriptor.Capability.TARGET_VERIFY);

        SourcePage<T> page = TransferValidation.require(
                source.read(scope, currentCheckpoint), "source page");
        TransferCheckpoint nextCheckpoint = page.nextCheckpoint();
        if (!currentCheckpoint.transferRunId().equals(nextCheckpoint.transferRunId())) {
            throw new IllegalStateException("source connector changed transferRunId");
        }
        if (nextCheckpoint.sequence() != currentCheckpoint.sequence() + 1) {
            throw new IllegalStateException("source connector did not advance exactly one sequence");
        }

        CanonicalTransferBatch<T> batch = CanonicalTransferBatch.create(
                transferFormatVersion,
                canonicalModelVersion,
                sourceDescriptor.adapterProfileVersion(),
                scope.organizationId(),
                scope.domain(),
                nextCheckpoint,
                page.objects());

        TargetPreflight preflight = TransferValidation.require(
                target.preflight(batch), "target preflight");
        if (!preflight.accepted()) {
            throw new IllegalStateException(
                    "target preflight rejected transfer: " + preflight.blockingReasonCodes());
        }

        String idempotencyKey = idempotencyKey(
                currentCheckpoint,
                batch,
                targetDescriptor);
        TargetApplyReceipt receipt = TransferValidation.require(
                target.apply(batch, idempotencyKey), "target receipt");
        if (!targetDescriptor.connectorKey().equals(receipt.connectorKey())) {
            throw new IllegalStateException("target receipt came from another connector");
        }
        if (!idempotencyKey.equals(receipt.idempotencyKey())) {
            throw new IllegalStateException("target receipt changed the idempotency key");
        }

        TargetVerification verification = TransferValidation.require(
                target.verify(receipt), "target verification");
        if (!verification.equivalent()) {
            throw new IllegalStateException(
                    "target readback differs from canonical state: "
                            + verification.differenceCodes());
        }

        return new TransferResult<>(
                batch,
                idempotencyKey,
                receipt,
                verification,
                nextCheckpoint);
    }

    private static void requireDomain(
            CanonicalObjectRef.Domain expected,
            CanonicalObjectRef.Domain actual,
            String owner) {
        if (expected != actual) {
            throw new IllegalArgumentException(owner + " has a different domain");
        }
    }

    private static void requireCapability(
            ConnectorDescriptor descriptor,
            ConnectorDescriptor.Capability capability) {
        if (!descriptor.supports(capability)) {
            throw new IllegalStateException(
                    descriptor.connectorKey() + " does not support " + capability);
        }
    }

    private static String idempotencyKey(
            TransferCheckpoint currentCheckpoint,
            CanonicalTransferBatch<?> batch,
            ConnectorDescriptor targetDescriptor) {
        MessageDigest digest = sha256();
        update(digest, currentCheckpoint.transferRunId().toString());
        update(digest, batch.domain().name());
        update(digest, batch.sequence());
        update(digest, batch.aggregateSha256());
        update(digest, targetDescriptor.connectorKey());
        update(digest, targetDescriptor.adapterProfileVersion());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
