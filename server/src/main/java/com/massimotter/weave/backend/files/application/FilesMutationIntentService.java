package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Pins the active Files provider binding before any protocol mutation may reach an adapter. */
public final class FilesMutationIntentService {

    private static final String DOMAIN = "files";
    private static final String PROFILE = "weave.webdav.files/v1";

    private final OperationIntentService intents;
    private final ProviderBindingRepository bindings;

    public FilesMutationIntentService(OperationIntentService intents, ProviderBindingRepository bindings) {
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
    }

    public PinnedMutation begin(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        ProviderBinding binding = bindings.current(command.organizationRef(), DOMAIN)
                .filter(candidate -> candidate.state() == State.ACTIVE)
                .orElseThrow(() -> new ProviderBindingUnavailableException(command.organizationRef()));
        String argumentsDigest = digest(command.canonicalArguments());
        String idempotencyKey = normalizeIdempotencyKey(
                command.idempotencyKey(), command.organizationRef(), command.personRef(),
                command.operation(), argumentsDigest);
        var result = intents.begin(new BeginCommand(
                idempotencyKey,
                command.organizationRef(),
                new HumanActor(command.personRef(), command.subjectRef()),
                DOMAIN,
                new ProtocolProjection("webdav", command.operation(), PROFILE),
                digest(command.operation()),
                argumentsDigest,
                command.objectRefs(),
                command.policyRevision(),
                command.entitlementRevision(),
                binding.revision()));
        return new PinnedMutation(result.intent(), binding, result.retry());
    }

    public PinnedMutation dispatch(PinnedMutation mutation) {
        if (mutation.intent().state() != OperationIntent.State.CREATED) {
            return mutation;
        }
        return mutation.withIntent(intents.markDispatching(mutation.intent()));
    }

    public void requireAdapter(PinnedMutation mutation, String adapterKey) {
        if (!mutation.binding().adapterKey().equals(adapterKey)) {
            throw new PinnedAdapterMismatchException(mutation.binding().adapterKey(), adapterKey);
        }
    }

    public PinnedMutation ambiguous(PinnedMutation mutation, String supportSafeCorrelation) {
        return mutation.withIntent(intents.markAmbiguous(
                mutation.intent(), digest(supportSafeCorrelation == null ? mutation.intent().operationRef() : supportSafeCorrelation)));
    }

    public PinnedMutation reconcile(PinnedMutation mutation) {
        if (mutation.intent().state() != OperationIntent.State.AMBIGUOUS) {
            return mutation;
        }
        return mutation.withIntent(intents.beginReconciliation(mutation.intent()));
    }

    public PinnedMutation succeed(PinnedMutation mutation, String canonicalResult, String auditRef) {
        return mutation.withIntent(intents.succeed(mutation.intent(), digest(canonicalResult), auditRef));
    }

    public PinnedMutation fail(PinnedMutation mutation, String canonicalResult, String auditRef) {
        return mutation.withIntent(intents.fail(mutation.intent(), digest(canonicalResult), auditRef));
    }

    private String normalizeIdempotencyKey(
            String supplied, String organizationRef, String personRef, String operation, String argumentsDigest) {
        if (supplied != null && !supplied.isBlank()) {
            String normalized = supplied.trim();
            if (normalized.length() < 16 || normalized.length() > 128) {
                throw new InvalidIdempotencyKeyException();
            }
            return normalized;
        }
        return "webdav-auto:" + digest(organizationRef + "\n" + personRef + "\n" + operation + "\n" + argumentsDigest)
                .substring("sha256:".length());
    }

    public static String digest(String value) {
        return digest(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
    }

    public static String digest(byte[] value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value == null ? new byte[0] : value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record Command(
            String idempotencyKey,
            String organizationRef,
            String personRef,
            String subjectRef,
            String operation,
            String canonicalArguments,
            List<String> objectRefs,
            String policyRevision,
            String entitlementRevision) {

        public Command {
            objectRefs = objectRefs == null ? List.of() : List.copyOf(objectRefs);
        }
    }

    public record PinnedMutation(OperationIntent intent, ProviderBinding binding, boolean retry) {
        private PinnedMutation withIntent(OperationIntent updated) {
            return new PinnedMutation(updated, binding, retry);
        }
    }

    public static final class ProviderBindingUnavailableException extends RuntimeException {
        public ProviderBindingUnavailableException(String organizationRef) {
            super("active Files provider binding is unavailable for " + organizationRef);
        }
    }

    public static final class PinnedAdapterMismatchException extends RuntimeException {
        public PinnedAdapterMismatchException(String pinned, String configured) {
            super("pinned Files adapter " + pinned + " does not match configured adapter " + configured);
        }
    }

    public static final class InvalidIdempotencyKeyException extends RuntimeException {
        public InvalidIdempotencyKeyException() {
            super("idempotency key length must be between 16 and 128 characters");
        }
    }
}
