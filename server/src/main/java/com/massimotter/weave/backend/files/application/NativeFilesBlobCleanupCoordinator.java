package com.massimotter.weave.backend.files.application;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.CleanupWork;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.RecordedDisposition;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.ReferenceStatus;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReceipt;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Callable bounded consumer for reserved terminal-failure Files cleanup work. */
@Service
public class NativeFilesBlobCleanupCoordinator {
    public static final int MAX_BINDINGS_PER_CALL = 100;

    private final FilesBlobCleanupDispositionRepository dispositions;
    private final BlobStorePort blobs;
    private final Clock clock;
    private final Function<String, String> bindingDigester;

    @Autowired
    public NativeFilesBlobCleanupCoordinator(
            FilesBlobCleanupDispositionRepository dispositions,
            BlobStorePort blobs) {
        this(dispositions, blobs, Clock.systemUTC(), FilesDigests::sha256);
    }

    NativeFilesBlobCleanupCoordinator(
            FilesBlobCleanupDispositionRepository dispositions,
            BlobStorePort blobs,
            Clock clock,
            Function<String, String> bindingDigester) {
        this.dispositions = requireNonNull(dispositions, "dispositions");
        this.blobs = requireNonNull(blobs, "blobs");
        this.clock = requireNonNull(clock, "clock");
        this.bindingDigester = requireNonNull(bindingDigester, "bindingDigester");
    }

    @Transactional
    public CleanupResult process(String operationRef, int limit) {
        if (limit < 1 || limit > MAX_BINDINGS_PER_CALL) {
            throw new IllegalArgumentException("cleanup limit must be between 1 and 100");
        }
        if (!blobs.configured()) {
            throw new NativeFilesBlobCleanupException("Files blob cleanup storage is not configured");
        }

        CleanupWork work = dispositions.lockWork(operationRef);
        Map<String, PlannedBinding> planned = planned(work);
        Map<String, RecordedDisposition> recorded = validateRecorded(
                work,
                planned,
                dispositions.recorded(work.operationRef()));

        int processed = 0;
        for (PlannedBinding candidate : planned.values()) {
            if (recorded.containsKey(candidate.digest()) || processed >= limit) {
                continue;
            }
            Disposition disposition = selectDisposition(work, candidate.binding());
            dispositions.record(
                    work,
                    candidate.binding(),
                    candidate.digest(),
                    disposition,
                    clock.instant());
            processed++;
        }

        Map<String, RecordedDisposition> after = validateRecorded(
                work,
                planned,
                dispositions.recorded(work.operationRef()));
        boolean complete = after.size() == planned.size()
                && after.keySet().equals(planned.keySet());
        EnumMap<Disposition, Integer> counts = new EnumMap<>(Disposition.class);
        for (Disposition disposition : Disposition.values()) {
            counts.put(disposition, 0);
        }
        after.values().forEach(row -> counts.compute(row.disposition(), (key, value) -> value + 1));
        return new CleanupResult(
                work.operationRef(),
                planned.size(),
                after.size(),
                processed,
                counts.get(Disposition.STILL_REFERENCED),
                counts.get(Disposition.STILL_PROTECTED),
                counts.get(Disposition.DELETED),
                counts.get(Disposition.ALREADY_ABSENT),
                complete);
    }

    private Disposition selectDisposition(CleanupWork work, BlobReference binding) {
        ReferenceStatus status = dispositions.recheck(work, binding);
        if (status == ReferenceStatus.STILL_REFERENCED) {
            return Disposition.STILL_REFERENCED;
        }
        if (status == ReferenceStatus.STILL_PROTECTED) {
            return Disposition.STILL_PROTECTED;
        }

        Optional<BlobReceipt> before = blobs.receipt(work.scope(), binding);
        before.ifPresent(receipt -> requireMatchingReceipt(binding, receipt));
        blobs.delete(work.scope(), binding);
        Optional<BlobReceipt> after = blobs.receipt(work.scope(), binding);
        if (after.isPresent()) {
            throw new NativeFilesBlobCleanupException(
                    "Files blob cleanup could not prove deletion");
        }
        return before.isPresent() ? Disposition.DELETED : Disposition.ALREADY_ABSENT;
    }

    private void requireMatchingReceipt(BlobReference binding, BlobReceipt receipt) {
        if (!binding.equals(receipt.reference())) {
            throw new NativeFilesBlobCleanupException(
                    "Files blob cleanup storage returned inconsistent evidence");
        }
    }

    private Map<String, PlannedBinding> planned(CleanupWork work) {
        List<BlobReference> ordered = new ArrayList<>(work.plannedBindings());
        ordered.sort(Comparator.comparing(BlobReference::value));
        Map<String, PlannedBinding> planned = new LinkedHashMap<>();
        Map<String, String> bindingByDigest = new LinkedHashMap<>();
        for (BlobReference binding : ordered) {
            String digest = requireDigest(bindingDigester.apply(binding.value()));
            String collision = bindingByDigest.putIfAbsent(digest, binding.value());
            if (collision != null && !collision.equals(binding.value())) {
                throw new NativeFilesBlobCleanupException(
                        "Files blob cleanup binding digest collision");
            }
            planned.putIfAbsent(digest, new PlannedBinding(binding, digest));
        }
        return planned;
    }

    private Map<String, RecordedDisposition> validateRecorded(
            CleanupWork work,
            Map<String, PlannedBinding> planned,
            List<RecordedDisposition> stored) {
        Map<String, RecordedDisposition> valid = new LinkedHashMap<>();
        for (RecordedDisposition row : stored == null ? List.<RecordedDisposition>of() : stored) {
            PlannedBinding expected = planned.get(row.bindingDigest());
            if (!work.operationRef().equals(row.operationRef())
                    || expected == null
                    || !expected.binding().equals(row.binding())
                    || !FilesBlobCleanupDispositionRepository.VERSION.equals(
                            row.dispositionVersion())
                    || valid.putIfAbsent(row.bindingDigest(), row) != null) {
                throw new NativeFilesBlobCleanupException(
                        "Files blob cleanup disposition evidence is inconsistent");
            }
        }
        return valid;
    }

    private String requireDigest(String value) {
        String digest = value == null ? "" : value;
        if (!digest.matches("sha256:[a-f0-9]{64}")) {
            throw new NativeFilesBlobCleanupException(
                    "Files blob cleanup binding digest is invalid");
        }
        return digest;
    }

    private record PlannedBinding(BlobReference binding, String digest) {
    }

    /** Support-safe progress only; exact bindings and binding digests are deliberately absent. */
    public record CleanupResult(
            String operationRef,
            int plannedCount,
            int recordedCount,
            int processedCount,
            int stillReferencedCount,
            int stillProtectedCount,
            int deletedCount,
            int alreadyAbsentCount,
            boolean complete) {
    }
}
