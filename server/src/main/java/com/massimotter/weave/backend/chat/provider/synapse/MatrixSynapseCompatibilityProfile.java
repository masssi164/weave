package com.massimotter.weave.backend.chat.provider.synapse;

import java.util.List;
import java.util.Set;

/**
 * Versioned Matrix/Synapse callback-classification contract. Production remains
 * pinned to the dogfood profile. Selecting a fixture target here neither
 * changes the deployed provider version nor recommends a pin change; disposable
 * provider conformance evidence is recorded separately from this runtime model.
 */
public record MatrixSynapseCompatibilityProfile(
        String profileId,
        String synapseVersion,
        String matrixRoomVersion,
        String applicationServiceRegistrationProfile,
        String classifierVersion,
        String semanticFingerprintVersion,
        Set<String> supportedIgnoredStateTypes) {

    public static final String PINNED_SYNAPSE_VERSION = "1.136.0";
    public static final String CANDIDATE_SYNAPSE_VERSION = "1.156.0";
    public static final String MATRIX_ROOM_VERSION = "10";
    public static final String APPLICATION_SERVICE_REGISTRATION_PROFILE =
            "exclusive-user-alias-namespaces-rooms-empty-receive-ephemeral-false-v1";
    public static final String CLASSIFIER_VERSION = "matrix-synapse-state-v1";
    public static final String SEMANTIC_FINGERPRINT_VERSION = "matrix-as-event-set-v1";

    private static final Set<String> BASE_SUPPORTED_IGNORED_STATE_TYPES = Set.of(
            "m.room.create",
            "m.room.member",
            "m.room.name",
            "m.room.canonical_alias",
            "m.room.encryption",
            "m.room.power_levels",
            "m.room.join_rules",
            "m.room.history_visibility",
            "m.room.guest_access");

    public MatrixSynapseCompatibilityProfile {
        profileId = required(profileId, "profile ID");
        synapseVersion = required(synapseVersion, "Synapse version");
        matrixRoomVersion = required(matrixRoomVersion, "Matrix room version");
        applicationServiceRegistrationProfile = required(
                applicationServiceRegistrationProfile, "Application Service registration profile");
        classifierVersion = required(classifierVersion, "classifier version");
        semanticFingerprintVersion = required(semanticFingerprintVersion, "semantic fingerprint version");
        supportedIgnoredStateTypes = supportedIgnoredStateTypes == null
                ? Set.of()
                : Set.copyOf(supportedIgnoredStateTypes);
    }

    public static MatrixSynapseCompatibilityProfile pinned() {
        return profile(PINNED_SYNAPSE_VERSION);
    }

    public static MatrixSynapseCompatibilityProfile candidate() {
        return profile(CANDIDATE_SYNAPSE_VERSION);
    }

    public static List<MatrixSynapseCompatibilityProfile> classifierFixtureTargets() {
        return List.of(pinned(), candidate());
    }

    public MatrixSynapseCompatibilityProfile withReclassifiedState(
            String nextClassifierVersion,
            String eventType) {
        java.util.HashSet<String> supported = new java.util.HashSet<>(supportedIgnoredStateTypes);
        supported.add(required(eventType, "Matrix event type"));
        return new MatrixSynapseCompatibilityProfile(
                profileId + "+" + required(nextClassifierVersion, "classifier version"),
                synapseVersion,
                matrixRoomVersion,
                applicationServiceRegistrationProfile,
                nextClassifierVersion,
                semanticFingerprintVersion,
                supported);
    }

    /** State is identified by state_key presence; type only selects its disposition. */
    public StateClassification classify(String eventType, boolean stateKeyPresent) {
        if (stateKeyPresent) {
            return supportedIgnoredStateTypes.contains(eventType)
                    ? StateClassification.SUPPORTED_IGNORED
                    : StateClassification.UNKNOWN_RECOVERABLE;
        }
        return supportedIgnoredStateTypes.contains(eventType)
                ? StateClassification.KNOWN_STATE_KEY_MISSING
                : StateClassification.NOT_STATE;
    }

    private static MatrixSynapseCompatibilityProfile profile(String synapseVersion) {
        return new MatrixSynapseCompatibilityProfile(
                "matrix-synapse-" + synapseVersion + "-room-v" + MATRIX_ROOM_VERSION + "-as-v1",
                synapseVersion,
                MATRIX_ROOM_VERSION,
                APPLICATION_SERVICE_REGISTRATION_PROFILE,
                CLASSIFIER_VERSION,
                SEMANTIC_FINGERPRINT_VERSION,
                BASE_SUPPORTED_IGNORED_STATE_TYPES);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 160
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Matrix/Synapse " + label + " is invalid.");
        }
        return value.trim();
    }

    public enum StateClassification {
        NOT_STATE,
        SUPPORTED_IGNORED,
        UNKNOWN_RECOVERABLE,
        KNOWN_STATE_KEY_MISSING
    }
}
