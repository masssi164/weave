package com.massimotter.weave.core.transfer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Versioned bounded transfer envelope for one canonical domain and organization. */
public record CanonicalTransferBatch<T>(
        String transferFormatVersion,
        String canonicalModelVersion,
        String sourceAdapterProfileVersion,
        String organizationId,
        CanonicalObjectRef.Domain domain,
        long sequence,
        TransferCheckpoint nextCheckpoint,
        List<CanonicalTransferObject<T>> objects,
        String aggregateSha256) {

    public CanonicalTransferBatch {
        transferFormatVersion = TransferValidation.requireText(
                transferFormatVersion, "transferFormatVersion");
        canonicalModelVersion = TransferValidation.requireText(
                canonicalModelVersion, "canonicalModelVersion");
        sourceAdapterProfileVersion = TransferValidation.requireText(
                sourceAdapterProfileVersion, "sourceAdapterProfileVersion");
        organizationId = TransferValidation.requireText(organizationId, "organizationId");
        domain = TransferValidation.require(domain, "domain");
        nextCheckpoint = TransferValidation.require(nextCheckpoint, "nextCheckpoint");
        objects = List.copyOf(TransferValidation.require(objects, "objects"));
        aggregateSha256 = TransferValidation.requireSha256(
                aggregateSha256, "aggregateSha256");

        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (nextCheckpoint.sequence() != sequence) {
            throw new IllegalArgumentException("checkpoint sequence must equal batch sequence");
        }
        if (nextCheckpoint.domain() != domain) {
            throw new IllegalArgumentException("checkpoint domain must equal batch domain");
        }

        Set<String> objectKeys = new HashSet<>();
        for (CanonicalTransferObject<T> object : objects) {
            TransferValidation.require(object, "object");
            CanonicalObjectRef reference = object.reference();
            if (!organizationId.equals(reference.organizationId())) {
                throw new IllegalArgumentException("batch contains a different organization");
            }
            if (domain != reference.domain()) {
                throw new IllegalArgumentException("batch contains a different domain");
            }
            if (!canonicalModelVersion.equals(reference.canonicalModelVersion())) {
                throw new IllegalArgumentException("batch contains a different canonical model version");
            }
            if (!objectKeys.add(reference.stableKey())) {
                throw new IllegalArgumentException(
                        "batch contains duplicate object " + reference.stableKey());
            }
        }

        String expectedDigest = calculateAggregateSha256(
                transferFormatVersion,
                canonicalModelVersion,
                organizationId,
                domain,
                sequence,
                objects);
        if (!expectedDigest.equals(aggregateSha256)) {
            throw new IllegalArgumentException("aggregateSha256 does not match canonical content");
        }
    }

    public static <T> CanonicalTransferBatch<T> create(
            String transferFormatVersion,
            String canonicalModelVersion,
            String sourceAdapterProfileVersion,
            String organizationId,
            CanonicalObjectRef.Domain domain,
            TransferCheckpoint nextCheckpoint,
            List<CanonicalTransferObject<T>> objects) {
        String digest = calculateAggregateSha256(
                transferFormatVersion,
                canonicalModelVersion,
                organizationId,
                domain,
                nextCheckpoint.sequence(),
                objects);
        return new CanonicalTransferBatch<>(
                transferFormatVersion,
                canonicalModelVersion,
                sourceAdapterProfileVersion,
                organizationId,
                domain,
                nextCheckpoint.sequence(),
                nextCheckpoint,
                objects,
                digest);
    }

    private static String calculateAggregateSha256(
            String transferFormatVersion,
            String canonicalModelVersion,
            String organizationId,
            CanonicalObjectRef.Domain domain,
            long sequence,
            List<? extends CanonicalTransferObject<?>> objects) {
        MessageDigest digest = sha256();
        update(digest, TransferValidation.requireText(
                transferFormatVersion, "transferFormatVersion"));
        update(digest, TransferValidation.requireText(
                canonicalModelVersion, "canonicalModelVersion"));
        update(digest, TransferValidation.requireText(organizationId, "organizationId"));
        update(digest, TransferValidation.require(domain, "domain").name());
        update(digest, sequence);

        List<CanonicalTransferObject<?>> sortedObjects = new ArrayList<>(objects);
        sortedObjects.sort(Comparator.comparing(object -> object.reference().stableKey()));
        for (CanonicalTransferObject<?> object : sortedObjects) {
            updateObject(digest, object);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateObject(
            MessageDigest digest,
            CanonicalTransferObject<?> object) {
        CanonicalObjectRef reference = object.reference();
        update(digest, reference.stableKey());
        update(digest, reference.canonicalModelVersion());
        update(digest, reference.revision());
        update(digest, reference.lifecycle().name());
        update(digest, object.payloadSha256());
        update(digest, object.provenance().kind().name());
        update(digest, object.provenance().observedAt().toEpochMilli());

        List<CanonicalObjectRef> dependencies = new ArrayList<>(object.dependencies());
        dependencies.sort(Comparator.naturalOrder());
        for (CanonicalObjectRef dependency : dependencies) {
            update(digest, dependency.stableKey());
            update(digest, dependency.canonicalModelVersion());
            update(digest, dependency.revision());
            update(digest, dependency.lifecycle().name());
        }

        List<FidelityFinding> findings = new ArrayList<>(object.fidelityFindings());
        findings.sort(Comparator.comparing(FidelityFinding::fieldPath));
        for (FidelityFinding finding : findings) {
            update(digest, finding.fieldPath());
            update(digest, finding.classification().name());
            update(digest, finding.reasonCode());
            ArchiveReference archive = finding.archiveReference();
            if (archive == null) {
                update(digest, "-");
            } else {
                update(digest, archive.archiveId());
                update(digest, archive.mediaType());
                update(digest, archive.sha256());
                update(digest, archive.byteCount());
            }
        }
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
