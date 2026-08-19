package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepository;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.schema.SchemaAuthorityTestSupport;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves application-consistent recovery of canonical Files metadata plus private
 * OpenDAL filesystem blobs through the direct canonical native composition.
 */
@Tag("postgres")
class CanonicalFilesBackupRestoreTest {

    private static final String CANDIDATE = "c".repeat(40);
    private static final String DATABASE_DUMP = "/tmp/weave-files.dump";
    private static final long MAXIMUM_BLOB_BYTES = 4L * 1024L * 1024L;
    private static final FilesRequestScope SCOPE =
            new FilesRequestScope("organization-files", "space-primary", 1);
    private static final PostgreSQLContainer<?> SOURCE = postgres("weave_files_source");
    private static final PostgreSQLContainer<?> TARGET = postgres("weave_files_target");

    static {
        SOURCE.start();
        TARGET.start();
    }

    @Test
    void restoresCanonicalMetadataAndPrivateBlobsThenContinuesWriting(
            @TempDir Path directory) throws Exception {
        Path sourceBlobRoot = directory.resolve("source-blobs");
        Path targetBlobRoot = directory.resolve("target-blobs");
        Path sourceReceipt = directory.resolve("source-schema-receipt.json");
        Path targetReceipt = directory.resolve("target-schema-receipt.json");
        Map<String, String> sourceEnvironment = environment(SOURCE, sourceReceipt);
        Map<String, String> targetEnvironment = environment(TARGET, targetReceipt);

        SchemaAuthorityTestSupport.initializeAndVerify(sourceEnvironment);
        DriverManagerDataSource sourceDataSource = dataSource(SOURCE);
        JpaFilesAuthorityRepository sourceAuthority =
                FilesAuthorityJpaTestFactory.create(sourceDataSource);
        WeaveNativeFilesProperties sourceProperties = properties(sourceBlobRoot);
        FilesystemBlobStore sourceBlobs = new FilesystemBlobStore(sourceProperties);

        byte[] originalContent = "Weave canonical Files recovery\n"
                .getBytes(StandardCharsets.UTF_8);
        FileObject originalFile;
        VersionedFile originalVersion;
        try {
            CanonicalNativeFilesComposition source = composition(
                    sourceAuthority,
                    sourceBlobs,
                    sourceProperties);
            FilesProviderPort files = source.scoped(SCOPE);
            files.createCollection(new FilePath("/docs"));
            originalFile = files.write(new FileWrite(
                    new FilePath("/docs/recovery.txt"),
                    originalContent,
                    "text/plain; charset=utf-8"));
            originalVersion = files.find(new FilePath("/docs/recovery.txt"))
                    .orElseThrow();
            assertThat(files.read(originalFile.id()).bytes())
                    .containsExactly(originalContent);
            assertHealthy(source.reconcile(SCOPE), 2, 1);
        } finally {
            sourceBlobs.closeOperator();
        }

        Path databaseDump = directory.resolve("weave-files.dump");
        Path blobArchive = directory.resolve("weave-files-blobs.zip");
        dumpDatabase(SOURCE, databaseDump);
        archiveBlobTree(sourceBlobRoot, blobArchive);
        assertThat(Files.size(databaseDump)).isPositive();
        assertThat(Files.size(blobArchive)).isPositive();

        restoreDatabase(TARGET, databaseDump);
        restoreBlobTree(blobArchive, targetBlobRoot);
        SchemaAuthorityTestSupport.initializeAndVerify(targetEnvironment);

        JsonNode sourceSchemaReceipt = receipt(sourceReceipt);
        JsonNode targetSchemaReceipt = receipt(targetReceipt);
        assertThat(targetSchemaReceipt.path("migrationsExecuted").asInt()).isZero();
        assertThat(targetSchemaReceipt.path("catalogFingerprint").asText())
                .isEqualTo(sourceSchemaReceipt.path("catalogFingerprint").asText());

        DriverManagerDataSource targetDataSource = dataSource(TARGET);
        JpaTestDatabase.validateSchema(targetDataSource);
        JpaFilesAuthorityRepository targetAuthority =
                FilesAuthorityJpaTestFactory.create(targetDataSource);
        WeaveNativeFilesProperties targetProperties = properties(targetBlobRoot);
        FilesystemBlobStore targetBlobs = new FilesystemBlobStore(targetProperties);
        try {
            CanonicalNativeFilesComposition restored = composition(
                    targetAuthority,
                    targetBlobs,
                    targetProperties);
            FilesProviderPort files = restored.scoped(SCOPE);

            VersionedFile restoredVersion = files.find(
                    new FilePath("/docs/recovery.txt")).orElseThrow();
            assertThat(restoredVersion.item().id()).isEqualTo(originalFile.id());
            assertThat(restoredVersion.item().path()).isEqualTo(originalFile.path());
            assertThat(restoredVersion.version()).isEqualTo(originalVersion.version());
            assertThat(files.read(restoredVersion.item().id()).bytes())
                    .containsExactly(originalContent);
            assertHealthy(restored.reconcile(SCOPE), 2, 1);

            byte[] continuedContent = "Written only after isolated restore\n"
                    .getBytes(StandardCharsets.UTF_8);
            FileObject continued = files.write(new FileWrite(
                    new FilePath("/docs/after-restore.txt"),
                    continuedContent,
                    "text/plain; charset=utf-8"));
            assertThat(files.read(continued.id()).bytes())
                    .containsExactly(continuedContent);
            assertHealthy(restored.reconcile(SCOPE), 3, 2);
        } finally {
            targetBlobs.closeOperator();
        }

        assertThat(sourceAuthority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath("/docs/after-restore.txt")))
                .isEmpty();
        assertThat(sourceAuthority.activeFiles(
                SCOPE.organizationRef(),
                SCOPE.spaceRef()))
                .hasSize(2);
        JpaTestDatabase.validateSchema(targetDataSource);
    }

    private static CanonicalNativeFilesComposition composition(
            JpaFilesAuthorityRepository authority,
            FilesystemBlobStore blobs,
            WeaveNativeFilesProperties properties) {
        return new CanonicalNativeFilesComposition(
                authority,
                blobs,
                Clock.systemUTC(),
                properties.reconciliationLimit());
    }

    private static void assertHealthy(
            CanonicalFilesQueries.ReconciliationReport report,
            int expectedMetadata,
            int expectedBlobs) {
        assertThat(report.activeMetadataRecords()).isEqualTo(expectedMetadata);
        assertThat(report.inventoriedBlobs()).isEqualTo(expectedBlobs);
        assertThat(report.orphanBlobsDeleted()).isZero();
        assertThat(report.inconsistentMetadataRecords()).isZero();
    }

    private static PostgreSQLContainer<?> postgres(String databaseName) {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.9-alpine"))
                .withDatabaseName(databaseName)
                .withUsername("weave")
                .withPassword("weave-test-only");
    }

    private static Map<String, String> environment(
            PostgreSQLContainer<?> postgres,
            Path receipt) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("WEAVE_PERSISTENCE_URL", postgres.getJdbcUrl());
        values.put("WEAVE_PERSISTENCE_USERNAME", postgres.getUsername());
        values.put("WEAVE_PERSISTENCE_PASSWORD", postgres.getPassword());
        values.put("WEAVE_CANDIDATE_COMMIT", CANDIDATE);
        values.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString());
        return Map.copyOf(values);
    }

    private static DriverManagerDataSource dataSource(
            PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(postgres.getDriverClassName());
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private static WeaveNativeFilesProperties properties(Path root) {
        return new WeaveNativeFilesProperties(root, MAXIMUM_BLOB_BYTES, 100);
    }

    private static void dumpDatabase(
            PostgreSQLContainer<?> postgres,
            Path target) throws Exception {
        execute(
                postgres,
                "create canonical Files PostgreSQL dump",
                "PGPASSWORD=\"$POSTGRES_PASSWORD\" pg_dump "
                        + "--format=custom --compress=6 --serializable-deferrable "
                        + "--no-owner --no-privileges "
                        + "--username=\"$POSTGRES_USER\" --dbname=\"$POSTGRES_DB\" "
                        + "--file=" + DATABASE_DUMP);
        postgres.copyFileFromContainer(DATABASE_DUMP, target.toString());
    }

    private static void restoreDatabase(
            PostgreSQLContainer<?> postgres,
            Path source) throws Exception {
        postgres.copyFileToContainer(
                MountableFile.forHostPath(source),
                DATABASE_DUMP);
        execute(
                postgres,
                "restore canonical Files PostgreSQL dump",
                "PGPASSWORD=\"$POSTGRES_PASSWORD\" pg_restore "
                        + "--single-transaction --exit-on-error --no-owner --no-privileges "
                        + "--username=\"$POSTGRES_USER\" --dbname=\"$POSTGRES_DB\" "
                        + DATABASE_DUMP);
    }

    private static void execute(
            PostgreSQLContainer<?> postgres,
            String description,
            String script) throws Exception {
        ExecResult result = postgres.execInContainer("sh", "-euc", script);
        assertThat(result.getExitCode())
                .as(description + ": " + supportSafe(
                        result.getStderr(),
                        postgres.getPassword()))
                .isZero();
    }

    private static void archiveBlobTree(
            Path root,
            Path archive) throws Exception {
        assertThat(root).isDirectory();
        if (Files.isSymbolicLink(root)) {
            throw new IllegalStateException("blob root must not be a symbolic link");
        }
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths
                    .filter(path -> Files.isRegularFile(
                            path,
                            LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        }
        assertThat(files).isNotEmpty();
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(
                archive,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE))) {
            for (Path file : files) {
                if (Files.isSymbolicLink(file)) {
                    throw new IllegalStateException(
                            "blob archive must not follow symbolic links");
                }
                String name = root.relativize(file)
                        .toString()
                        .replace(File.separatorChar, '/');
                if (unsafeArchiveName(name)) {
                    throw new IllegalStateException(
                            "blob archive contains an unsafe entry");
                }
                output.putNextEntry(new ZipEntry(name));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        privateFile(archive);
    }

    private static void restoreBlobTree(
            Path archive,
            Path root) throws Exception {
        Files.createDirectories(root);
        privateDirectory(root);
        try (ZipInputStream input = new ZipInputStream(
                Files.newInputStream(archive))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
                String name = entry.getName();
                if (entry.isDirectory() || unsafeArchiveName(name)) {
                    throw new IllegalStateException(
                            "blob archive contains an unsupported entry");
                }
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root)
                        || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException(
                            "blob archive target failed containment or uniqueness");
                }
                Path parent = target.getParent();
                Files.createDirectories(parent);
                privateDirectories(root, parent);
                Files.copy(input, target);
                privateFile(target);
                input.closeEntry();
            }
        }
    }

    private static boolean unsafeArchiveName(String name) {
        if (name == null || name.isBlank()
                || name.indexOf('\\') >= 0
                || name.startsWith("/")) {
            return true;
        }
        return List.of(name.split("/", -1)).stream()
                .anyMatch(segment -> segment.isBlank()
                        || ".".equals(segment)
                        || "..".equals(segment));
    }

    private static void privateDirectories(
            Path root,
            Path directory) throws IOException {
        Path current = root;
        for (Path segment : root.relativize(directory)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalStateException(
                        "restored blob directory must not be a symbolic link");
            }
            privateDirectory(current);
        }
    }

    private static void privateDirectory(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path,
                    PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX developer workstations rely on platform ACLs.
        }
    }

    private static void privateFile(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX developer workstations rely on platform ACLs.
        }
    }

    private static JsonNode receipt(Path path) throws Exception {
        return new ObjectMapper().readTree(Files.readString(path));
    }

    private static String supportSafe(String value, String secret) {
        String safe = value == null ? "" : value;
        return safe.replace(secret, "<redacted>")
                .replaceAll("[\\r\\n]+", " ")
                .strip();
    }
}
