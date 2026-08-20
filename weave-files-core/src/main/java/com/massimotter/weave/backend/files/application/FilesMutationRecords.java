package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;

/** Reconstructs the exact planned result metadata without reading mutable latest state. */
public final class FilesMutationRecords {

    private FilesMutationRecords() {
    }

    public static StoredFileRecord resultRecord(Sealed plan, Target target) {
        FilePath resultPath = new FilePath(target.resultLifecycleState()
                == com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED
                ? target.sourcePath()
                : target.targetPath());
        FileId id = new FileId(target.targetFileRef());
        FileVersion version = target.objectKind() == Kind.FILE
                ? new FileVersion(target.resultFileVersion())
                : new FileVersion(FilesDigests.sha256(
                        "collection\u0000" + id.value() + "\u0000" + resultPath.value()));
        FileObject object = new FileObject(
                id,
                resultPath,
                target.objectKind(),
                target.resultSize(),
                target.resultMediaType(),
                target.resultModifiedAt(),
                target.resultHidden());
        if (target.objectKind() == Kind.FILE
                && !FilesEtags.strong(object, version).equals(target.resultStrongEtag())) {
            throw new IllegalArgumentException("the planned Files result ETag is invalid");
        }
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        plan.organizationRef(),
                        plan.spaceRef(),
                        object,
                        version,
                        target.resultContentDigest(),
                        plan.providerBindingRevision(),
                        target.resultLifecycleState(),
                        target.resultObservedAt()),
                target.resultBlobBinding() == null ? null : new BlobBinding(target.resultBlobBinding()));
    }
}
