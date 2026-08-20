package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Pins the active Files provider binding before any protocol mutation may reach an adapter. */
public final class FilesMutationIntentService {

    private static final String DOMAIN = "files";
    private static final String PROFILE = "weave.webdav.files/v1";

    private final OperationIntentService intents;
    private final ProviderBindingRepository bindings;
    private final NativeFilesMutationRepository nativeMutations;

    public FilesMutationIntentService(OperationIntentService intents, ProviderBindingRepository bindings) {
        this(intents, bindings, null);
    }

    public FilesMutationIntentService(
            OperationIntentService intents,
            ProviderBindingRepository bindings,
            NativeFilesMutationRepository nativeMutations) {
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
        this.nativeMutations = nativeMutations;
    }

    public PinnedMutation begin(Command command) {
        PreparedMutation prepared = prepare(command);
        var result = intents.begin(prepared.beginCommand());
        return new PinnedMutation(result.intent(), prepared.binding(), result.retry());
    }

    public PreparedMutation prepare(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        String argumentsDigest = digest(command.canonicalArguments());
        String suppliedKey = suppliedIdempotencyKey(command.idempotencyKey());
        OperationIntent existing = suppliedKey == null
                ? null
                : intents.findExisting(command.organizationRef(), suppliedKey).orElse(null);
        ProviderBinding binding = existing == null
                ? currentBinding(command.organizationRef())
                : bindings.revision(command.organizationRef(), DOMAIN, existing.providerBindingRevision())
                        .orElseThrow(() -> new ProviderBindingUnavailableException(command.organizationRef()));
        String idempotencyKey = suppliedKey == null
                ? automaticIdempotencyKey(
                        command.organizationRef(),
                        command.personRef(),
                        command.operation(),
                        argumentsDigest,
                        binding.revision())
                : suppliedKey;
        BeginCommand beginCommand = new BeginCommand(
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
                binding.revision());
        if (existing == null) {
            existing = intents.findExisting(command.organizationRef(), idempotencyKey)
                    .map(intent -> intents.requireEquivalent(intent, beginCommand).intent())
                    .orElse(null);
        } else {
            existing = intents.requireEquivalent(existing, beginCommand).intent();
        }
        return new PreparedMutation(
                existing == null ? intents.prepare(beginCommand) : existing,
                binding,
                beginCommand,
                existing != null);
    }

    public NativePinnedMutation beginNative(PreparedMutation prepared, Sealed plan) {
        return beginNative(
                prepared,
                new FilesScope(plan.organizationRef(), plan.spaceRef()),
                () -> plan);
    }

    public NativePinnedMutation beginNative(
            PreparedMutation prepared,
            FilesScope scope,
            Supplier<Sealed> planFactory) {
        if (nativeMutations == null) {
            throw new IllegalStateException("native Files mutation repository is unavailable");
        }
        PreparedMutation requested = Objects.requireNonNull(prepared, "prepared must not be null");
        FilesScope requiredScope = Objects.requireNonNull(scope, "scope must not be null");
        NativeFilesMutationRepository.BeginResult result = nativeMutations.begin(
                requested.candidate(),
                requiredScope,
                Objects.requireNonNull(planFactory, "planFactory must not be null"));
        intents.requireEquivalent(result.intent(), requested.beginCommand());
        return new NativePinnedMutation(
                result.intent(),
                requested.binding(),
                result.plan(),
                !result.created());
    }

    public NativePinnedMutation resumeNative(PreparedMutation prepared) {
        if (nativeMutations == null) {
            throw new IllegalStateException("native Files mutation repository is unavailable");
        }
        PreparedMutation requested = Objects.requireNonNull(prepared, "prepared must not be null");
        if (!requested.retry()) {
            throw new IllegalArgumentException("a new native Files mutation has no committed plan to resume");
        }
        return new NativePinnedMutation(
                requested.candidate(),
                requested.binding(),
                nativeMutations.requireSealed(requested.candidate().operationRef()),
                true);
    }

    public OperationIntent failNative(
            NativePinnedMutation mutation,
            String canonicalResult,
            String auditRef) {
        if (nativeMutations == null) {
            throw new IllegalStateException("native Files mutation repository is unavailable");
        }
        return nativeMutations.recordFailure(
                mutation.intent(),
                false,
                digest(canonicalResult),
                auditRef);
    }

    public PinnedMutation dispatch(PinnedMutation mutation) {
        if (mutation.intent().state() != OperationIntent.State.CREATED) {
            return mutation;
        }
        return mutation.withIntent(intents.markDispatching(mutation.intent()));
    }

    public void requireAdapter(PinnedMutation mutation, String adapterKey) {
        requireAdapter(mutation.binding(), adapterKey);
    }

    public void requireAdapter(PreparedMutation mutation, String adapterKey) {
        requireAdapter(mutation.binding(), adapterKey);
    }

    public void requireAdapter(NativePinnedMutation mutation, String adapterKey) {
        requireAdapter(mutation.binding(), adapterKey);
    }

    private void requireAdapter(ProviderBinding binding, String adapterKey) {
        if (!binding.adapterKey().equals(adapterKey)) {
            throw new PinnedAdapterMismatchException(binding.adapterKey(), adapterKey);
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

    private ProviderBinding currentBinding(String organizationRef) {
        return bindings.current(organizationRef, DOMAIN)
                .filter(candidate -> candidate.state() == State.ACTIVE)
                .orElseThrow(() -> new ProviderBindingUnavailableException(organizationRef));
    }

    private String suppliedIdempotencyKey(String supplied) {
        if (supplied != null && !supplied.isBlank()) {
            String normalized = supplied.trim();
            if (normalized.length() < 16 || normalized.length() > 128) {
                throw new InvalidIdempotencyKeyException();
            }
            return normalized;
        }
        return null;
    }

    private String automaticIdempotencyKey(
            String organizationRef,
            String personRef,
            String operation,
            String argumentsDigest,
            long providerBindingRevision) {
        return "webdav-auto:" + digest(organizationRef
                        + "\n" + personRef
                        + "\n" + operation
                        + "\n" + argumentsDigest
                        + "\n" + providerBindingRevision)
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

    public record PreparedMutation(
            OperationIntent candidate,
            ProviderBinding binding,
            BeginCommand beginCommand,
            boolean retry) {
    }

    public record NativePinnedMutation(
            OperationIntent intent,
            ProviderBinding binding,
            Sealed plan,
            boolean retry) {
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
