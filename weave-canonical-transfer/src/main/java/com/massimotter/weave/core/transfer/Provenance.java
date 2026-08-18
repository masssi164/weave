package com.massimotter.weave.core.transfer;

import java.time.Instant;

/** Provider-neutral origin metadata. Any source binding reference is private transfer metadata. */
public record Provenance(
        Kind kind,
        Instant observedAt,
        String sourceBindingRef,
        String transformationCode) {

    public Provenance {
        kind = TransferValidation.require(kind, "kind");
        observedAt = TransferValidation.require(observedAt, "observedAt");
        sourceBindingRef = TransferValidation.optionalText(sourceBindingRef);
        transformationCode = TransferValidation.optionalText(transformationCode);

        if (kind == Kind.NATIVE && sourceBindingRef != null) {
            throw new IllegalArgumentException("native provenance must not name a provider binding");
        }
        if (kind == Kind.TRANSFORMED && transformationCode == null) {
            throw new IllegalArgumentException("transformed provenance requires transformationCode");
        }
    }

    public static Provenance nativeObject(Instant observedAt) {
        return new Provenance(Kind.NATIVE, observedAt, null, null);
    }

    public static Provenance imported(Instant observedAt, String sourceBindingRef) {
        return new Provenance(Kind.IMPORTED, observedAt, sourceBindingRef, null);
    }

    public enum Kind {
        NATIVE,
        IMPORTED,
        OBSERVED,
        TRANSFORMED,
        RESTORED
    }
}
