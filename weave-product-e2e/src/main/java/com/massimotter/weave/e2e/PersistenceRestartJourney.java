package com.massimotter.weave.e2e;

import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Bounded bridge to the isolated Compose persistence-restart proof. */
final class PersistenceRestartJourney {
  private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(7);
  private static final Duration PROCESS_CLEANUP_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAXIMUM_DIAGNOSTIC_BYTES = 32_768;
  private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
      PosixFilePermissions.fromString("rw-------");

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;

  PersistenceRestartJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
  }

  RestartProof restart() {
    Path command = environment.persistenceRestartCommand();
    Path evidence = environment.persistenceRestartEvidence();
    Path processOutput = evidence.resolveSibling(".persistence-restart-process.log");
    Process process = null;
    try {
      Files.deleteIfExists(evidence);
      Files.deleteIfExists(processOutput);
      Files.createFile(processOutput);
      setPrivatePermissions(processOutput);
      process =
          new ProcessBuilder(
                  "bash",
                  command.toString(),
                  "test",
                  "persistence-restart-proof")
              .redirectErrorStream(true)
              .redirectOutput(processOutput.toFile())
              .start();
      boolean completed =
          process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      if (!completed) {
        BoundedProcessTree.terminate(process, PROCESS_CLEANUP_TIMEOUT);
        throw new ProductFlowException("persistence restart proof exceeded its bounded timeout");
      }
      String diagnostic = boundedDiagnostic(processOutput);
      if (process.exitValue() != 0
          || !diagnostic.contains("WEAVE_PERSISTENCE_RESTART_RESULT")) {
        throw new ProductFlowException(
            "persistence restart proof failed: " + safeDiagnostic(diagnostic));
      }
      JsonNode receipt = readReceipt(evidence);
      requireReceipt(receipt);
      return new RestartProof(
          Hashing.sha256(Files.readString(evidence, StandardCharsets.UTF_8)),
          receipt.path("postgres").path("restartObserved").asBoolean(false),
          receipt.path("runtimeState").path("restartObserved").asBoolean(false),
          receipt
              .path("runtimeState")
              .path("fixtureRestoredExactly")
              .asBoolean(false));
    } catch (IOException failure) {
      throw new ProductFlowException("persistence restart proof could not be executed", failure);
    } catch (InterruptedException interrupted) {
      throw BoundedProcessTree.interruptedFailure(
          process,
          PROCESS_CLEANUP_TIMEOUT,
          "persistence restart proof was interrupted",
          interrupted);
    } finally {
      try {
        Files.deleteIfExists(processOutput);
      } catch (IOException ignored) {
        // The bounded output is support-safe and remains in the private run directory.
      }
    }
  }

  private JsonNode readReceipt(Path evidence) throws IOException {
    if (Files.isSymbolicLink(evidence)
        || !Files.isRegularFile(evidence, LinkOption.NOFOLLOW_LINKS)
        || Files.size(evidence) > 32_768) {
      throw new ProductFlowException(
          "persistence restart proof omitted its bounded regular evidence file");
    }
    return http.mapper().readTree(Files.readAllBytes(evidence));
  }

  private void requireReceipt(JsonNode receipt) {
    boolean valid =
        "weave.test-app-persistence-restart/v1"
                .equals(receipt.path("schemaVersion").asString())
            && environment
                .candidateCommit()
                .equals(receipt.path("candidateCommit").asString())
            && environment
                .specificationCommit()
                .equals(receipt.path("specificationCommit").asString())
            && environment
                .candidateManifestDigest()
                .equals(receipt.path("candidateManifestDigest").asString())
            && environment
                .composeProject()
                .equals(receipt.path("composeProject").asString())
            && receipt.path("postgres").path("sameContainer").asBoolean(false)
            && receipt.path("postgres").path("restartObserved").asBoolean(false)
            && receipt.path("postgres").path("healthyAfterRestart").asBoolean(false)
            && receipt
                .path("postgres")
                .path("dependentKeycloakRestartObserved")
                .asBoolean(false)
            && receipt
                .path("postgres")
                .path("keycloakHealthyAfterRestart")
                .asBoolean(false)
            && receipt.path("runtimeState").path("sameContainer").asBoolean(false)
            && receipt.path("runtimeState").path("restartObserved").asBoolean(false)
            && receipt
                .path("runtimeState")
                .path("healthyAfterRestart")
                .asBoolean(false)
            && receipt.path("runtimeState").path("sameVolume").asBoolean(false)
            && receipt
                .path("runtimeState")
                .path("fixtureRestoredExactly")
                .asBoolean(false)
            && receipt.path("runtimeState").path("fixtureRemoved").asBoolean(false)
            && "live-product-state"
                .equals(receipt.path("classification").path("postgres").asString())
            && "live-integration-fixture"
                .equals(receipt.path("classification").path("runtimeState").asString())
            && !receipt.path("credentialsIncluded").asBoolean(true)
            && !receipt.path("containsSecretValues").asBoolean(true)
            && receipt.path("supportSafe").asBoolean(false);
    if (!valid) {
      throw new ProductFlowException("persistence restart evidence is incomplete");
    }
  }

  private static String boundedDiagnostic(Path output) throws IOException {
    byte[] bytes = Files.readAllBytes(output);
    int offset = Math.max(0, bytes.length - MAXIMUM_DIAGNOSTIC_BYTES);
    return new String(
        bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
  }

  private static String safeDiagnostic(String diagnostic) {
    String safe =
        diagnostic
            .replaceAll("https?://\\S+", "[uri-redacted]")
            .replaceAll("(?i)bearer\\s+\\S+", "bearer [redacted]")
            .replaceAll(
                "(?i)(token|password|assertion|secret|private[_-]?key)=\\S+",
                "$1=[redacted]")
            .replaceAll("(?m)^.*(?:/Users/|/home/|/run/secrets/).*$", "[path-redacted]");
    return safe.length() <= 1_024 ? safe.strip() : safe.substring(safe.length() - 1_024).strip();
  }

  private static void setPrivatePermissions(Path path) throws IOException {
    try {
      Files.setPosixFilePermissions(path, OWNER_FILE_PERMISSIONS);
    } catch (UnsupportedOperationException ignored) {
      // The parent run directory remains private on non-POSIX file systems.
    }
  }

  record RestartProof(
      String evidenceSha256,
      boolean postgresRestartObserved,
      boolean runtimeStateRestartObserved,
      boolean runtimeStateFixtureRestored) {}
}
