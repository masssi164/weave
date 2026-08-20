package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReceipt;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Performs only the idempotent blob effects named by a freshly verified sealed plan. */
public final class CanonicalFilesBlobEffects {

    private final BlobStorePort blobs;

    public CanonicalFilesBlobEffects(BlobStorePort blobs) {
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
    }

    public List<BlobReceipt> execute(Sealed plan, byte[] putContent) {
        Sealed required = Objects.requireNonNull(plan, "plan must not be null");
        return switch (required.operationKind()) {
            case PUT -> put(required, putContent);
            case COPY -> copy(required);
            case MKCOL, MOVE, DELETE -> List.of();
        };
    }

    private List<BlobReceipt> put(Sealed plan, byte[] supplied) {
        List<Target> files = plan.targets().stream()
                .filter(target -> target.objectKind() == Kind.FILE
                        && (target.changeKind() == ChangeKind.CREATED
                        || target.changeKind() == ChangeKind.CONTENT_UPDATED))
                .toList();
        if (files.size() != 1) {
            throw new BlobEffectException("a PUT plan must contain exactly one file result");
        }
        byte[] content = supplied == null ? new byte[0] : supplied.clone();
        Target target = files.getFirst();
        verify(content, target.resultSize(), target.resultContentDigest());
        Optional<BlobReceipt> existing = existing(plan, target);
        if (existing.isPresent()) {
            return List.of(existing.get());
        }
        return List.of(publish(plan, target, content));
    }

    private List<BlobReceipt> copy(Sealed plan) {
        List<BlobReceipt> receipts = new ArrayList<>();
        for (Target target : plan.targets()) {
            if (target.changeKind() != ChangeKind.COPIED || target.objectKind() != Kind.FILE) {
                continue;
            }
            Optional<BlobReceipt> existing = existing(plan, target);
            if (existing.isPresent()) {
                receipts.add(existing.get());
                continue;
            }
            if (target.sourceReadBlobBinding() == null
                    || target.sourceSize() == null
                    || target.sourceContentDigest() == null) {
                throw new BlobEffectException("a copied file is missing its protected source snapshot");
            }
            ByteArrayOutputStream content = new ByteArrayOutputStream(
                    Math.toIntExact(Math.min(target.sourceSize(), Integer.MAX_VALUE)));
            blobs.readStream(
                    scope(plan),
                    reference(target.sourceReadBlobBinding()),
                    content);
            byte[] verified = content.toByteArray();
            verify(verified, target.sourceSize(), target.sourceContentDigest());
            receipts.add(publish(plan, target, verified));
        }
        return List.copyOf(receipts);
    }

    private Optional<BlobReceipt> existing(Sealed plan, Target target) {
        BlobReference reference = reference(target.resultBlobBinding());
        Optional<BlobReceipt> receipt = blobs.receipt(scope(plan), reference);
        receipt.ifPresent(existing -> {
            if (existing.size() != target.resultSize()
                    || !constantTime(existing.digest(), target.resultContentDigest())
                    || !existing.reference().equals(reference)) {
                throw new BlobEffectException("an existing blob does not match the sealed Files plan");
            }
        });
        return receipt;
    }

    private BlobReceipt publish(Sealed plan, Target target, byte[] content) {
        BlobReceipt receipt = blobs.putStream(
                scope(plan),
                reference(target.resultBlobBinding()),
                new ByteArrayInputStream(content),
                target.resultSize(),
                target.resultContentDigest());
        if (receipt.size() != target.resultSize()
                || !constantTime(receipt.digest(), target.resultContentDigest())
                || !receipt.reference().value().equals(target.resultBlobBinding())) {
            throw new BlobEffectException("the blob receipt does not match the sealed Files plan");
        }
        return receipt;
    }

    private void verify(byte[] content, long expectedSize, String expectedDigest) {
        String actual = FilesDigests.sha256(content);
        if (content.length != expectedSize || !constantTime(actual, expectedDigest)) {
            throw new BlobEffectException("the supplied Files content does not match the sealed plan");
        }
    }

    private boolean constantTime(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private BlobReference reference(String value) {
        try {
            return new BlobReference(value);
        } catch (IllegalArgumentException invalid) {
            throw new BlobEffectException("the sealed plan contains an invalid blob binding", invalid);
        }
    }

    private BlobScope scope(Sealed plan) {
        return new BlobScope(plan.organizationRef(), plan.spaceRef());
    }

    public static final class BlobEffectException extends RuntimeException {
        public BlobEffectException(String message) {
            super(message);
        }

        public BlobEffectException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
