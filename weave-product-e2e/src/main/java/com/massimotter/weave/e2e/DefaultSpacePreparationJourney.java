package com.massimotter.weave.e2e;

import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** Exercises the same bounded Space setup used before physical owner testing. */
final class DefaultSpacePreparationJourney {
  private static final Duration TIMEOUT = Duration.ofMinutes(12);
  private static final Duration RESTORATION_TIMEOUT = Duration.ofMinutes(4);
  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;

  DefaultSpacePreparationJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
  }

  void prepare(String expectedOutcome) {
    if (!List.of("CREATED", "VERIFIED").contains(expectedOutcome)) {
      throw new IllegalArgumentException("Unknown default Space setup outcome");
    }
    Path receipt = environment.evidenceFile().resolveSibling(
        "default-space-" + expectedOutcome.toLowerCase(java.util.Locale.ROOT) + ".json");
    JsonNode result = runOperation(receipt,
        List.of("bootstrap-space", "--evidence-file", receipt.toString()));
    requireReceipt(result, environment.sourceCandidateCommit(),
        environment.specificationCommit(), expectedOutcome);
    System.out.println("WEAVE_DEFAULT_SPACE_JOURNEY outcome=" + expectedOutcome
        + " ownerAccessVerified=true temporaryAuthorityAbsent=true supportSafe=true");
  }

  String inviteOwner(String email, String idempotencyKey) {
    Path request = environment.evidenceFile().resolveSibling("owner-bootstrap-request.json");
    Path receipt = environment.evidenceFile().resolveSibling("owner-bootstrap-preparation.json");
    boolean created = false;
    try {
      Files.createFile(request, PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString("rw-------")));
      created = true;
      var body = http.mapper().createObjectNode();
      body.put("email", email);
      body.put("displayName", "Weave E2E Owner");
      body.put("idempotencyKey", idempotencyKey);
      Files.writeString(request, http.mapper().writeValueAsString(body));
      JsonNode result = runOperation(receipt, List.of("bootstrap-owner", "--request-file",
          request.toString(), "--evidence-file", receipt.toString()));
      if (!"weave-owner-bootstrap-evidence-v2".equals(result.path("schemaVersion").asString())
          || !"owner".equals(result.path("requestedRole").asString())
          || !Hashing.sha256(email.toLowerCase(java.util.Locale.ROOT))
              .equals(result.path("emailSha256").asString())
          || result.path("organizationId").asString().isBlank()) {
        throw new ProductFlowException("Bounded owner invitation identity is invalid");
      }
      for (String field : List.of("mailMessageMatched", "bootstrapAuthorityAbsent",
          "bootstrapMountAbsent", "canonicalImageUnchanged", "tokenAbsent", "supportSafe")) {
        if (!result.path(field).isBoolean() || !result.path(field).asBoolean()) {
          throw new ProductFlowException("Bounded owner invitation cleanup is incomplete");
        }
      }
      return result.path("organizationId").asString();
    } catch (IOException failure) {
      throw new ProductFlowException("Bounded owner invitation request could not be prepared", failure);
    } finally {
      if (created) {
        try {
          Files.delete(request);
        } catch (IOException failure) {
          throw new ProductFlowException("Bounded owner invitation request cleanup failed", failure);
        }
      }
    }
  }

  private JsonNode runOperation(Path receipt, List<String> arguments) {
    Path diagnostic = receipt.resolveSibling(receipt.getFileName() + ".private.log");
    Process process = null;
    try {
      if (Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)
          || Files.exists(diagnostic, LinkOption.NOFOLLOW_LINKS)) {
        throw new ProductFlowException("Default Space setup output already exists");
      }
      Files.createFile(diagnostic, PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString("rw-------")));
      List<String> command = new ArrayList<>(List.of(
          "bash", environment.persistenceRestartCommand().toString(), "e2e"));
      command.addAll(arguments);
      process = new ProcessBuilder(command)
          .redirectErrorStream(true).redirectOutput(diagnostic.toFile()).start();
      if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        throw terminateOperation(process,
            new ProductFlowException("Development setup exceeded its bounded timeout"));
      }
      if (process.exitValue() != 0 || Files.isSymbolicLink(receipt)
          || !Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)
          || Files.size(receipt) > 32_768) {
        throw new ProductFlowException("Default Space setup failed before bounded evidence");
      }
      return http.mapper().readTree(Files.readAllBytes(receipt));
    } catch (IOException failure) {
      throw new ProductFlowException("Default Space setup could not be executed", failure);
    } catch (InterruptedException interrupted) {
      try {
        throw terminateOperation(process,
            new ProductFlowException("Development setup was interrupted", interrupted));
      } finally {
        Thread.currentThread().interrupt();
      }
    }
  }

  static ProductFlowException terminateOperation(Process process, ProductFlowException failure) {
    if (process == null) {
      return failure;
    }
    // Allow the Python lifecycle's SIGTERM handler to restore the normal Server
    // before resorting to forced process-tree cleanup.
    process.destroy();
    long deadline = System.nanoTime() + RESTORATION_TIMEOUT.toNanos();
    boolean interrupted = false;
    try {
      while (process.isAlive() && System.nanoTime() < deadline) {
        try {
          process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
          interrupted = true;
        }
      }
      if (process.isAlive()) {
        return BoundedProcessTree.terminatePreservingFailure(
            process, Duration.ofSeconds(10), failure);
      }
      return failure;
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  static void requireReceipt(JsonNode receipt, String candidate, String spec, String outcome) {
    if (receipt == null || !receipt.isObject()
        || !"weave.default-space-preparation/v1".equals(receipt.path("schemaVersion").asString())
        || !candidate.equals(receipt.path("candidateCommit").asString())
        || !spec.equals(receipt.path("specificationCommit").asString())
        || !"passed".equals(receipt.path("status").asString())
        || !"workspace-default".equals(receipt.path("spaceRef").asString())
        || !outcome.equals(receipt.path("outcome").asString())
        || !receipt.path("containsSecretValues").isBoolean()
        || receipt.path("containsSecretValues").asBoolean()) {
      throw new ProductFlowException("Default Space setup evidence identity is invalid");
    }
    for (String key : List.of("ownerAccessVerified", "filesReady", "calendarReady",
        "bootstrapAuthorityAbsent", "bootstrapMountAbsent", "tokenAbsent",
        "canonicalImageUnchanged", "supportSafe")) {
      if (!receipt.path(key).isBoolean() || !receipt.path(key).asBoolean()) {
        throw new ProductFlowException("Default Space setup evidence is incomplete field=" + key);
      }
    }
  }
}
