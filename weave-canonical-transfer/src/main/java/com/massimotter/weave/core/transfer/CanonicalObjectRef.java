package com.massimotter.weave.core.transfer;

/** Stable provider-independent identity and revision of one canonical collaboration object. */
public record CanonicalObjectRef(
        String organizationId,
        Domain domain,
        String objectType,
        String objectId,
        String canonicalModelVersion,
        long revision,
        Lifecycle lifecycle) implements Comparable<CanonicalObjectRef> {

    public CanonicalObjectRef {
        organizationId = TransferValidation.requireText(organizationId, "organizationId");
        domain = TransferValidation.require(domain, "domain");
        objectType = TransferValidation.requireText(objectType, "objectType");
        objectId = TransferValidation.requireText(objectId, "objectId");
        canonicalModelVersion = TransferValidation.requireText(
                canonicalModelVersion, "canonicalModelVersion");
        lifecycle = TransferValidation.require(lifecycle, "lifecycle");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
    }

    public String stableKey() {
        return organizationId + '\u001f' + domain.name() + '\u001f' + objectType + '\u001f' + objectId;
    }

    @Override
    public int compareTo(CanonicalObjectRef other) {
        return stableKey().compareTo(other.stableKey());
    }

    public enum Domain {
        FILES,
        CALENDAR,
        CHAT
    }

    public enum Lifecycle {
        ACTIVE,
        TOMBSTONED,
        ARCHIVED
    }
}
