package com.massimotter.weave.backend.transfer.port;

import java.util.List;

import com.massimotter.weave.backend.transfer.domain.CanonicalTransferEnvelope;
import com.massimotter.weave.backend.transfer.domain.CanonicalTransferItem;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;

/** Applies canonical values to a target provider behind an idempotent anti-corruption boundary. */
public interface ProviderTargetConnector<T extends CanonicalTransferItem> {

    Preflight preflight(CanonicalTransferEnvelope<T> envelope);

    ApplyReceipt apply(CanonicalTransferEnvelope<T> envelope, String idempotencyKey);

    Verification verify(ApplyReceipt receipt);

    record Preflight(boolean accepted, List<LossRecord> losses, String rejectionReason) {
        public Preflight {
            losses = List.copyOf(losses == null ? List.of() : losses);
            rejectionReason = optionalText(rejectionReason);
            if (accepted && rejectionReason != null) {
                throw new IllegalArgumentException("accepted preflight must not contain a rejection reason");
            }
            if (!accepted && rejectionReason == null) {
                throw new IllegalArgumentException("rejected preflight requires a reason");
            }
        }

        public static Preflight accepted(List<LossRecord> losses) {
            return new Preflight(true, losses, null);
        }

        public static Preflight rejected(List<LossRecord> losses, String reason) {
            return new Preflight(false, losses, reason);
        }
    }

    record ApplyReceipt(String receiptId, String aggregateDigest, int appliedItems) {
        public ApplyReceipt {
            receiptId = required(receiptId, "receipt id");
            aggregateDigest = required(aggregateDigest, "aggregate digest");
            if (appliedItems < 0) {
                throw new IllegalArgumentException("appliedItems must not be negative");
            }
        }
    }

    record Verification(boolean equivalent, int verifiedItems, List<LossRecord> losses, String failureReason) {
        public Verification {
            if (verifiedItems < 0) {
                throw new IllegalArgumentException("verifiedItems must not be negative");
            }
            losses = List.copyOf(losses == null ? List.of() : losses);
            failureReason = optionalText(failureReason);
            if (equivalent && failureReason != null) {
                throw new IllegalArgumentException("equivalent verification must not contain a failure reason");
            }
            if (!equivalent && failureReason == null) {
                throw new IllegalArgumentException("failed verification requires a reason");
            }
        }

        public static Verification equivalent(int verifiedItems, List<LossRecord> losses) {
            return new Verification(true, verifiedItems, losses, null);
        }

        public static Verification failed(int verifiedItems, List<LossRecord> losses, String reason) {
            return new Verification(false, verifiedItems, losses, reason);
        }
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
