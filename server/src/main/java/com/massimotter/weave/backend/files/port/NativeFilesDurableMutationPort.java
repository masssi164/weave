package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.EntityTagCondition;
import com.massimotter.weave.backend.operation.domain.OperationIntent;

/** Server-private native extension; never a provider-neutral or northbound contract. */
public interface NativeFilesDurableMutationPort {

    Sealed plan(OperationIntent intent, FilesRequestScope scope, Mutation mutation);

    NativeResult execute(
            OperationIntent intent,
            FilesRequestScope scope,
            Sealed plan,
            Mutation mutation,
            String auditRef,
            NativeLockMove lockMove);

    sealed interface Mutation permits Put, MakeCollection, Copy, Move, Delete {
        FilePath resultPath();
        EntityTagCondition ifMatchCondition();
        EntityTagCondition ifNoneMatchCondition();
    }

    record Put(
            FileWrite write,
            EntityTagCondition ifMatchCondition,
            EntityTagCondition ifNoneMatchCondition) implements Mutation {
        public Put(FileWrite write) {
            this(write, EntityTagCondition.notSupplied(), EntityTagCondition.notSupplied());
        }
        @Override public FilePath resultPath() { return write.path(); }
    }

    record MakeCollection(
            FilePath path,
            EntityTagCondition ifMatchCondition,
            EntityTagCondition ifNoneMatchCondition) implements Mutation {
        public MakeCollection(FilePath path) {
            this(path, EntityTagCondition.notSupplied(), EntityTagCondition.notSupplied());
        }
        @Override public FilePath resultPath() { return path; }
    }

    record Copy(
            FilePath source,
            FilePath destination,
            boolean overwrite,
            EntityTagCondition ifMatchCondition,
            EntityTagCondition ifNoneMatchCondition) implements Mutation {
        public Copy(FilePath source, FilePath destination, boolean overwrite) {
            this(source, destination, overwrite,
                    EntityTagCondition.notSupplied(), EntityTagCondition.notSupplied());
        }
        @Override public FilePath resultPath() { return destination; }
    }

    record Move(
            FilePath source,
            FilePath destination,
            boolean overwrite,
            EntityTagCondition ifMatchCondition,
            EntityTagCondition ifNoneMatchCondition) implements Mutation {
        public Move(FilePath source, FilePath destination, boolean overwrite) {
            this(source, destination, overwrite,
                    EntityTagCondition.notSupplied(), EntityTagCondition.notSupplied());
        }
        @Override public FilePath resultPath() { return destination; }
    }

    record Delete(
            FilePath path,
            FileVersion expectedVersion,
            EntityTagCondition ifMatchCondition,
            EntityTagCondition ifNoneMatchCondition) implements Mutation {
        public Delete(FilePath path, FileVersion expectedVersion) {
            this(path, expectedVersion,
                    EntityTagCondition.notSupplied(), EntityTagCondition.notSupplied());
        }
        @Override public FilePath resultPath() { return path; }
    }

    record NativeResult(
            FileObject item,
            FileVersion version,
            String etag,
            boolean created) {
    }

    record NativeLockMove(
            FilePath source,
            FilePath destination,
            String tokenDigest,
            String ownerRef) {
    }
}
