package com.massimotter.weave.backend.files.domain;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Dependency;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ProvenanceKind;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CanonicalFileTest {

    private static final ModelVersion MODEL_VERSION = new ModelVersion("files-1");
    private static final Revision REVISION = new Revision(1);
    private static final Provenance PROVENANCE = new Provenance(
            ProvenanceKind.NATIVE, null, Instant.EPOCH);

    @Test
    void representsHierarchyThroughCanonicalIdsRatherThanPaths() {
        ObjectId parent = id("collection-1");
        CanonicalFile file = CanonicalFile.file(
                id("file-1"),
                MODEL_VERSION,
                REVISION,
                Lifecycle.ACTIVE,
                PROVENANCE,
                parent,
                "report.pdf",
                "application/pdf",
                42,
                "sha256:abc",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of());

        assertEquals(parent, file.parentId());
        assertEquals(
                List.of(new Dependency(file.objectId(), parent, "files.parent")),
                file.dependencies());
    }

    @Test
    void canonicalDigestIsStableAcrossRelatedDependencyOrder() {
        ObjectId fileId = id("file-1");
        Dependency calendar = new Dependency(
                fileId, new ObjectId(Domain.CALENDAR, "event-1"), "files.calendar-attachment");
        Dependency chat = new Dependency(
                fileId, new ObjectId(Domain.CHAT, "event-1"), "files.chat-attachment");

        CanonicalFile first = file(fileId, List.of(calendar, chat));
        CanonicalFile second = file(fileId, List.of(chat, calendar));

        assertEquals(first.canonicalDigest(), second.canonicalDigest());
    }

    @Test
    void collectionCannotCarryBlobOrContentMetadata() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalFile(
                        id("collection-1"),
                        MODEL_VERSION,
                        REVISION,
                        Lifecycle.ACTIVE,
                        PROVENANCE,
                        CanonicalFile.Kind.COLLECTION,
                        null,
                        "docs",
                        "application/octet-stream",
                        1,
                        "sha256:abc",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        List.of()));
    }

    @Test
    void rejectsProviderDomainAndPathShapedNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CanonicalFile.file(
                        new ObjectId(Domain.CHAT, "wrong-domain"),
                        MODEL_VERSION,
                        REVISION,
                        Lifecycle.ACTIVE,
                        PROVENANCE,
                        null,
                        "file.txt",
                        "text/plain",
                        1,
                        "sha256:abc",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> CanonicalFile.file(
                        id("file-1"),
                        MODEL_VERSION,
                        REVISION,
                        Lifecycle.ACTIVE,
                        PROVENANCE,
                        null,
                        "folder/file.txt",
                        "text/plain",
                        1,
                        "sha256:abc",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        List.of()));
    }

    @Test
    void publicRecordContainsNoStorageOrProviderIdentityComponent() {
        for (RecordComponent component : CanonicalFile.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("provider"), component.getName());
            assertFalse(name.contains("blob"), component.getName());
            assertFalse(name.contains("storage"), component.getName());
            assertFalse(name.contains("filesystem"), component.getName());
        }
    }

    private static CanonicalFile file(ObjectId objectId, List<Dependency> dependencies) {
        return CanonicalFile.file(
                objectId,
                MODEL_VERSION,
                REVISION,
                Lifecycle.ACTIVE,
                PROVENANCE,
                null,
                "file.txt",
                "text/plain",
                4,
                "sha256:text",
                Instant.EPOCH,
                Instant.EPOCH,
                dependencies);
    }

    private static ObjectId id(String value) {
        return new ObjectId(Domain.FILES, value);
    }
}
