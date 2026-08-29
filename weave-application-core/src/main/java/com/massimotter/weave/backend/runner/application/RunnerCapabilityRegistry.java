package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityDescriptor;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable public capability catalog, Runner offerings, and live Runner session state. */
public interface RunnerCapabilityRegistry {

    Pattern DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");
    Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    Pattern VERSION = Pattern.compile(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");

    PublicationResult publish(PublicBundlePublication publication);

    AvailabilityResult observeAvailability(AvailabilityObservation observation);

    CatalogSnapshot catalog(String organizationRef);

    List<RunnerOffering> offerings(String organizationRef, CapabilityRef capability);

    Optional<RunnerSession> session(String organizationRef, RunnerId runnerId);

    enum PublicationDisposition {
        CREATED,
        UPDATED,
        IDEMPOTENT_REPLAY
    }

    enum AvailabilityDisposition {
        CREATED,
        UPDATED,
        IDEMPOTENT_REPLAY
    }

    record CapabilityContract(CapabilityDescriptor descriptor, String contractDigest) {
        public CapabilityContract {
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            contractDigest = digest(contractDigest, "contractDigest");
        }

        public CapabilityRef capability() {
            return descriptor.capability();
        }
    }

    record PublicBundlePublication(
            RunnerId runnerId,
            String organizationRef,
            String bundleId,
            String bundleVersion,
            String publicBundleDigest,
            List<CapabilityContract> capabilities,
            RunnerState runnerState,
            int capacity,
            int availableSlots,
            Instant observedAt) {

        public PublicBundlePublication {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            organizationRef = organization(organizationRef);
            bundleId = identifier(bundleId, "bundleId");
            bundleVersion = version(bundleVersion, "bundleVersion");
            publicBundleDigest = digest(publicBundleDigest, "publicBundleDigest");
            capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            if (capabilities.isEmpty()
                    || capabilities.size() > 128
                    || capabilities.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "capabilities must contain between one and 128 values");
            }
            Set<String> coordinates = new HashSet<>();
            for (CapabilityContract capability : capabilities) {
                if (!coordinates.add(capability.capability().coordinate())) {
                    throw new IllegalArgumentException("duplicate capability coordinate");
                }
            }
            runnerState = Objects.requireNonNull(runnerState, "runnerState");
            validateCapacity(capacity, availableSlots);
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    record AvailabilityObservation(
            RunnerId runnerId,
            String organizationRef,
            String publicBundleDigest,
            String runnerVersion,
            RunnerState runnerState,
            int capacity,
            int runningTasks,
            Instant observedAt) {

        public AvailabilityObservation {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            organizationRef = organization(organizationRef);
            publicBundleDigest = digest(publicBundleDigest, "publicBundleDigest");
            runnerVersion = version(runnerVersion, "runnerVersion");
            runnerState = Objects.requireNonNull(runnerState, "runnerState");
            if (runnerState != RunnerState.ONLINE
                    && runnerState != RunnerState.DEGRADED
                    && runnerState != RunnerState.OFFLINE
                    && runnerState != RunnerState.REVOKED) {
                throw new IllegalArgumentException("heartbeat Runner state is unsupported");
            }
            if (capacity < 1 || capacity > 1024) {
                throw new IllegalArgumentException("capacity must be between one and 1024");
            }
            if (runningTasks < 0 || runningTasks > capacity) {
                throw new IllegalArgumentException(
                        "runningTasks must be between zero and capacity");
            }
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
        }

        public int availableSlots() {
            return capacity - runningTasks;
        }
    }

    record PublicationResult(
            long catalogRevision,
            PublicationDisposition disposition,
            int capabilityDefinitions,
            int activeOfferings) {
        public PublicationResult {
            if (catalogRevision < 0 || capabilityDefinitions < 0 || activeOfferings < 0) {
                throw new IllegalArgumentException("publication counters must not be negative");
            }
            disposition = Objects.requireNonNull(disposition, "disposition");
        }
    }

    record AvailabilityResult(
            AvailabilityDisposition disposition,
            int updatedOfferings,
            int availableSlots) {
        public AvailabilityResult {
            disposition = Objects.requireNonNull(disposition, "disposition");
            if (updatedOfferings < 1 || availableSlots < 0 || availableSlots > 1024) {
                throw new IllegalArgumentException("availability result counters are invalid");
            }
        }
    }

    record CapabilityDefinition(
            UUID definitionId,
            String organizationRef,
            CapabilityContract contract,
            long introducedRevision,
            Instant createdAt) {
        public CapabilityDefinition {
            definitionId = Objects.requireNonNull(definitionId, "definitionId");
            organizationRef = organization(organizationRef);
            contract = Objects.requireNonNull(contract, "contract");
            if (introducedRevision < 1) {
                throw new IllegalArgumentException("introducedRevision must be positive");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    record CatalogSnapshot(
            String organizationRef,
            long revision,
            List<CapabilityDefinition> definitions) {
        public CatalogSnapshot {
            organizationRef = organization(organizationRef);
            if (revision < 0) {
                throw new IllegalArgumentException("revision must not be negative");
            }
            definitions = List.copyOf(definitions == null ? List.of() : definitions);
        }
    }

    record RunnerOffering(
            UUID offeringId,
            String organizationRef,
            RunnerId runnerId,
            CapabilityRef capability,
            String contractDigest,
            String publicBundleDigest,
            String bundleId,
            String bundleVersion,
            RunnerState runnerState,
            int capacity,
            int availableSlots,
            Instant observedAt,
            boolean active) {
        public RunnerOffering {
            offeringId = Objects.requireNonNull(offeringId, "offeringId");
            organizationRef = organization(organizationRef);
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            capability = Objects.requireNonNull(capability, "capability");
            contractDigest = digest(contractDigest, "contractDigest");
            publicBundleDigest = digest(publicBundleDigest, "publicBundleDigest");
            bundleId = identifier(bundleId, "bundleId");
            bundleVersion = version(bundleVersion, "bundleVersion");
            runnerState = Objects.requireNonNull(runnerState, "runnerState");
            validateCapacity(capacity, availableSlots);
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
        }

        public boolean available() {
            return active
                    && availableSlots > 0
                    && (runnerState == RunnerState.ONLINE || runnerState == RunnerState.DEGRADED);
        }
    }

    record RunnerSession(
            RunnerId runnerId,
            String organizationRef,
            String publicBundleDigest,
            String runnerVersion,
            RunnerState runnerState,
            int capacity,
            int runningTasks,
            int availableSlots,
            Instant observedAt) {
        public RunnerSession {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            organizationRef = organization(organizationRef);
            publicBundleDigest = digest(publicBundleDigest, "publicBundleDigest");
            runnerVersion = version(runnerVersion, "runnerVersion");
            runnerState = Objects.requireNonNull(runnerState, "runnerState");
            validateCapacity(capacity, availableSlots);
            if (runningTasks < 0 || runningTasks > capacity || availableSlots != capacity - runningTasks) {
                throw new IllegalArgumentException("Runner session counters are inconsistent");
            }
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    private static String organization(String value) {
        return bounded(required(value, "organizationRef"), 256, "organizationRef");
    }

    private static String identifier(String value, String field) {
        String normalized = required(value, field);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return normalized;
    }

    private static String version(String value, String field) {
        String normalized = required(value, field);
        if (!VERSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return normalized;
    }

    private static String digest(String value, String field) {
        String normalized = required(value, field);
        if (!DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a sha256 digest");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }

    private static String bounded(String value, int maximum, String field) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }

    private static void validateCapacity(int capacity, int availableSlots) {
        if (capacity < 1 || capacity > 1024) {
            throw new IllegalArgumentException("capacity must be between one and 1024");
        }
        if (availableSlots < 0 || availableSlots > capacity) {
            throw new IllegalArgumentException(
                    "availableSlots must be between zero and capacity");
        }
    }
}
