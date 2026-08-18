package com.massimotter.weave.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import com.massimotter.weave.backend.transfer.adapter.JpaTransferRunRepository;
import com.massimotter.weave.backend.transfer.adapter.TransferRunJpaTestFactory;
import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossClass;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;
import com.massimotter.weave.backend.transfer.domain.TransferRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Proves that a private PostgreSQL consistency dump restores canonical transfer
 * state into a separate empty server and remains writable through canonical ports.
 */
@Tag("postgres")
class CanonicalPostgresBackupRestoreTest {

  private static final String CANDIDATE = "b".repeat(40);
  private static final String DUMP_IN_CONTAINER = "/tmp/weave-canonical.dump";
  private static final PostgreSQLContainer<?> SOURCE = postgres("weave_source");
  private static final PostgreSQLContainer<?> TARGET = postgres("weave_target");

  static {
    SOURCE.start();
    TARGET.start();
  }

  @Test
  void restoresCanonicalTransferStateIntoIndependentPostgresAndResumesCheckpoint(
      @TempDir Path directory) throws Exception {
    Environment source = environment(SOURCE, directory.resolve("source-schema-receipt.json"));
    SchemaAuthorityInitializer.run(source.values());
    SchemaReceiptVerifier.verify(source.values());

    JpaTransferRunRepository sourceRepository =
        TransferRunJpaTestFactory.create(dataSource(SOURCE));
    Instant now = Instant.parse("2026-08-18T20:00:00Z");
    LossRecord loss = new LossRecord(
        new CanonicalObjectId("calendar-event-1"),
        "alarm",
        LossClass.MANUAL_REVIEW,
        "target requires explicit alarm confirmation");
    TransferRun firstBatch = TransferRun.initial(
            new TransferRun.Id("transfer-backup-restore"),
            "organization-1",
            "canonical-v1",
            new TransferFormatVersion(1),
            now)
        .advance(
            new TransferCheckpoint("source-page-1", 1),
            2,
            List.of(loss),
            "digest-source-page-1",
            false,
            now.plusSeconds(1));
    sourceRepository.save(firstBatch, 0);
    assertThat(sourceRepository.findById(firstBatch.id())).contains(firstBatch);
    SchemaCatalogFingerprint.Snapshot sourceCatalog = catalog(SOURCE);

    Path dump = directory.resolve("weave-canonical.dump");
    execute(
        SOURCE,
        "create PostgreSQL consistency dump",
        "PGPASSWORD=\"$POSTGRES_PASSWORD\" pg_dump "
            + "--format=custom --compress=6 --serializable-deferrable "
            + "--no-owner --no-privileges "
            + "--username=\"$POSTGRES_USER\" --dbname=\"$POSTGRES_DB\" "
            + "--file=" + DUMP_IN_CONTAINER);
    SOURCE.copyFileFromContainer(DUMP_IN_CONTAINER, dump.toString());
    assertThat(Files.size(dump)).isPositive();
    assertThat(sha256(dump)).matches("[0-9a-f]{64}");

    TARGET.copyFileToContainer(MountableFile.forHostPath(dump), DUMP_IN_CONTAINER);
    execute(
        TARGET,
        "restore PostgreSQL consistency dump",
        "PGPASSWORD=\"$POSTGRES_PASSWORD\" pg_restore "
            + "--single-transaction --exit-on-error --no-owner --no-privileges "
            + "--username=\"$POSTGRES_USER\" --dbname=\"$POSTGRES_DB\" "
            + DUMP_IN_CONTAINER);

    SchemaCatalogFingerprint.Snapshot restoredCatalog = catalog(TARGET);
    assertThat(restoredCatalog.sha256())
        .as("restored catalog differs from source: "
            + firstDifference(sourceCatalog.canonicalJson(), restoredCatalog.canonicalJson()))
        .isEqualTo(sourceCatalog.sha256());

    Environment target = environment(TARGET, directory.resolve("target-schema-receipt.json"));
    SchemaAuthorityInitializer.run(target.values());
    SchemaReceiptVerifier.verify(target.values());
    JsonNode sourceReceipt = receipt(source.receipt());
    JsonNode targetReceipt = receipt(target.receipt());
    assertThat(targetReceipt.path("migrationsExecuted").asInt()).isZero();
    assertThat(targetReceipt.path("catalogFingerprint").asText())
        .isEqualTo(sourceReceipt.path("catalogFingerprint").asText());

    DriverManagerDataSource targetDataSource = dataSource(TARGET);
    JpaTestDatabase.validateSchema(targetDataSource);
    JpaTransferRunRepository targetRepository =
        TransferRunJpaTestFactory.create(targetDataSource);
    assertThat(targetRepository.findById(firstBatch.id())).contains(firstBatch);

    TransferRun completed = firstBatch.advance(
        null,
        1,
        List.of(loss),
        "digest-completed-after-restore",
        true,
        now.plusSeconds(2));
    targetRepository.save(completed, firstBatch.stateRevision());
    assertThat(targetRepository.findById(completed.id())).contains(completed);

    // The target is an independent database. Continuing the restored run must not
    // mutate the original source instance.
    assertThat(sourceRepository.findById(firstBatch.id())).contains(firstBatch);
    JpaTestDatabase.validateSchema(targetDataSource);
  }

  private static PostgreSQLContainer<?> postgres(String databaseName) {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.9-alpine"))
        .withDatabaseName(databaseName)
        .withUsername("weave")
        .withPassword("weave-test-only");
  }

  private static Environment environment(
      PostgreSQLContainer<?> postgres,
      Path receipt) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("WEAVE_PERSISTENCE_URL", postgres.getJdbcUrl());
    values.put("WEAVE_PERSISTENCE_USERNAME", postgres.getUsername());
    values.put("WEAVE_PERSISTENCE_PASSWORD", postgres.getPassword());
    values.put("WEAVE_CANDIDATE_COMMIT", CANDIDATE);
    values.put("WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString());
    return new Environment(receipt, values);
  }

  private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(postgres.getDriverClassName());
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    return dataSource;
  }

  private static SchemaCatalogFingerprint.Snapshot catalog(
      PostgreSQLContainer<?> postgres) throws Exception {
    try (var connection = DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      return SchemaCatalogFingerprint.inspect(connection);
    }
  }

  private static void execute(
      PostgreSQLContainer<?> postgres,
      String description,
      String script) throws Exception {
    ExecResult result = postgres.execInContainer("sh", "-euc", script);
    assertThat(result.getExitCode())
        .as(description + ": " + supportSafe(result.getStderr(), postgres.getPassword()))
        .isZero();
  }

  private static String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(path)) {
      byte[] buffer = new byte[1024 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read > 0) {
          digest.update(buffer, 0, read);
        }
      }
    }
    return java.util.HexFormat.of().formatHex(digest.digest());
  }

  private static JsonNode receipt(Path path) throws Exception {
    return new ObjectMapper().readTree(Files.readString(path));
  }

  private static String firstDifference(String source, String restored) {
    int limit = Math.min(source.length(), restored.length());
    int index = 0;
    while (index < limit && source.charAt(index) == restored.charAt(index)) {
      index++;
    }
    if (index == source.length() && index == restored.length()) {
      return "none";
    }
    int from = Math.max(0, index - 120);
    int sourceTo = Math.min(source.length(), index + 240);
    int restoredTo = Math.min(restored.length(), index + 240);
    return "offset=" + index
        + ", source=..." + source.substring(from, sourceTo)
        + ", restored=..." + restored.substring(from, restoredTo);
  }

  private static String supportSafe(String value, String secret) {
    String safe = value == null ? "" : value;
    return safe.replace(secret, "<redacted>").replaceAll("[\\r\\n]+", " ").strip();
  }

  private record Environment(Path receipt, Map<String, String> values) {
    private Environment {
      values = Map.copyOf(values);
    }
  }
}
