package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.service.files.UnqualifiedLegacyFilesContentAdapter.LegacyFileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.ObjectStoragePort;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeaveS3FilesAdapterTest {

    @Test
    void writesCanonicalPathThroughInfrastructurePortAndReturnsProviderNeutralObject() {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        WeaveS3FilesAdapter adapter = new WeaveS3FilesAdapter(properties(), storage);

        var stored = adapter.writeLegacy(new LegacyFileWrite(
                new FilePath("/Team/notes.txt"), "core".getBytes(), "text/plain"));

        assertThat(storage.bytes.get("Team/notes.txt")).isEqualTo("core".getBytes());
        assertThat(stored.path().value()).isEqualTo("/Team/notes.txt");
        assertThat(stored.kind()).isEqualTo(Kind.FILE);
        assertThat(stored.id().value()).startsWith("files:").doesNotContain("weave-files", "minio");
    }

    @Test
    void listsOnlyDirectChildrenAndDeclaresExplicitPortabilityLimits() {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        storage.write("Team/readme.md", "hello world!".getBytes(), "text/markdown");
        storage.write("Team/Design/.weave-collection", new byte[0], "application/x-weave-collection");
        WeaveS3FilesAdapter adapter = new WeaveS3FilesAdapter(properties(), storage);

        var listing = adapter.list(new FilePath("/Team"));

        assertThat(listing.listing().children())
                .extracting(item -> item.path().value())
                .containsExactlyInAnyOrder("/Team/Design", "/Team/readme.md");
        assertThat(adapter.conformanceProfile().adapterKey()).isEqualTo("weave-s3-minio");
        assertThat(adapter.conformanceProfile().fieldMappings().get("share").name()).isEqualTo("UNSUPPORTED");
        assertThat(adapter.conformanceProfile().supportSafe()).isTrue();
    }

    private WeaveS3FilesProperties properties() {
        WeaveS3FilesProperties properties = new WeaveS3FilesProperties();
        properties.setEnabled(true);
        properties.setEndpoint(URI.create("http://minio.test:9000"));
        properties.setRegion("us-east-1");
        properties.setBucket("weave-files");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setPathStyle(true);
        return properties;
    }

    private static final class InMemoryObjectStorage implements ObjectStoragePort {
        private final Map<String, byte[]> bytes = new LinkedHashMap<>();
        private final Map<String, ObjectMetadata> metadata = new LinkedHashMap<>();
        private long version;

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public void check() {
        }

        @Override
        public Optional<ObjectMetadata> stat(String key) {
            return Optional.ofNullable(metadata.get(key));
        }

        @Override
        public List<ObjectEntry> list(String prefix) {
            return metadata.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(entry -> new ObjectEntry(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public byte[] read(String key) {
            byte[] value = bytes.get(key);
            if (value == null) {
                throw new ObjectStorageException(FailureCode.NOT_FOUND, "missing object", null);
            }
            return value.clone();
        }

        @Override
        public void write(String key, byte[] value, String contentType) {
            byte[] content = value == null ? new byte[0] : value.clone();
            bytes.put(key, content);
            metadata.put(key, new ObjectMetadata(
                    content.length,
                    contentType,
                    "v" + (++version),
                    Instant.parse("2026-07-22T03:00:00Z")));
        }

        @Override
        public void copy(String sourceKey, String targetKey) {
            ObjectMetadata source = metadata.get(sourceKey);
            if (source == null) {
                throw new ObjectStorageException(FailureCode.NOT_FOUND, "missing object", null);
            }
            write(targetKey, bytes.get(sourceKey), source.contentType());
        }

        @Override
        public void delete(String key) {
            bytes.remove(key);
            metadata.remove(key);
        }
    }
}
