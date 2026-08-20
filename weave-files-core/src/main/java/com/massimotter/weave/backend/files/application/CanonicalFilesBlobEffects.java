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
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.ContentMismatchException;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.ExactInputStream;
import java.io.IOException;
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

    public List<BlobReceipt> execute(Sealed plan, ReplayableFileContent putContent) {
        Sealed required = Objects.requireNonNull(plan, "plan must not be null");
        return switch (required.operationKind()) {
            case PUT -> put(required, putContent);
            case COPY -> copy(required);
            case MKCOL, MOVE, DELETE -> List.of();
        };
    }

    private List<BlobReceipt> put(Sealed plan, ReplayableFileContent supplied) {
        List<Target> files = plan.targets().stream()
                .filter(target -> target.objectKind() == Kind.FILE
                        && (target.changeKind() == ChangeKind.CREATED
                        || target.changeKind() == ChangeKind.CONTENT_UPDATED))
                .toList();
        if (files.size() != 1) {
            throw new BlobEffectException("a PUT plan must contain exactly one file result");
        }
        ReplayableFileContent content = Objects.requireNonNull(
                supplied,
                "a PUT blob effect requires replayable content");
        Target target = files.getFirst();
        verifyDescriptor(content, target);
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
            receipts.add(copy(plan, target));
        }
        return List.copyOf(receipts);
    }

    private BlobReceipt copy(Sealed plan, Target target) {
        try {
            return BoundedBlobTransfer.copy(
                    blobs,
                    scope(plan),
                    reference(target.sourceReadBlobBinding()),
                    reference(target.resultBlobBinding()),
                    target.sourceSize(),
                    target.sourceContentDigest(),
                    target.sourceMediaType() == null
                            ? "application/octet-stream"
                            : target.sourceMediaType());
        } catch (BoundedBlobTransfer.TransferException failure) {
            throw new BlobEffectException("the protected COPY source could not be streamed", failure);
        }
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

    private BlobReceipt publish(
            Sealed plan,
            Target target,
            ReplayableFileContent content) {
        verifyDescriptor(content, target);
        try (ExactInputStream source = content.openStream()) {
            BlobReceipt receipt = blobs.putStream(
                    scope(plan),
                    reference(target.resultBlobBinding()),
                    source,
                    target.resultSize(),
                    target.resultContentDigest());
            source.requireComplete();
            if (receipt.size() != target.resultSize()
                    || !constantTime(receipt.digest(), target.resultContentDigest())
                    || !receipt.reference().value().equals(target.resultBlobBinding())) {
                throw new BlobEffectException("the blob receipt does not match the sealed Files plan");
            }
            return receipt;
        } catch (IOException failure) {
            throw new BlobEffectException("the supplied Files content does not match the sealed plan", failure);
        } catch (RuntimeException failure) {
            if (causedByContentMismatch(failure)) {
                throw new BlobEffectException(
                        "the supplied Files content does not match the sealed plan",
                        failure);
            }
            throw failure;
        }
    }

    private void verifyDescriptor(ReplayableFileContent content, Target target) {
        if (content.sizeBytes() != target.resultSize()
                || !constantTime(content.sha256Digest(), target.resultContentDigest())
                || !Objects.equals(content.mediaType(), target.resultMediaType())) {
            throw new BlobEffectException("the supplied Files content does not match the sealed plan");
        }
    }

    private boolean causedByContentMismatch(Throwable failure) {
        for (Throwable current = failure; current != null && current != current.getCause(); current = current.getCause()) {
            if (current instanceof ContentMismatchException) {
                return true;
            }
        }
        return false;
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
