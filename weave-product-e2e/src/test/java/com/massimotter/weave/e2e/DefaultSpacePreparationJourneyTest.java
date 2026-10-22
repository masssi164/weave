package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

class DefaultSpacePreparationJourneyTest {
  private static final String CANDIDATE = "a".repeat(40);
  private static final String SPEC = "b".repeat(40);

  @Test
  void terminationAllowsLifecycleCleanupBeforeForcedProcessRemoval(@TempDir Path temporary) throws Exception {
    Path ready = temporary.resolve("ready");
    Path restored = temporary.resolve("restored");
    Process process = new ProcessBuilder("python3", "-c", """
        import signal, sys, time
        from pathlib import Path
        def restore(signum, frame):
            Path(sys.argv[2]).write_text('canonical-restored')
            raise SystemExit(0)
        signal.signal(signal.SIGTERM, restore)
        Path(sys.argv[1]).write_text('ready')
        while True:
            time.sleep(0.05)
        """, ready.toString(), restored.toString()).start();
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (!Files.exists(ready) && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertThat(Files.exists(ready)).isTrue();
      ProductFlowException original = new ProductFlowException("test setup timeout");
      assertThat(DefaultSpacePreparationJourney.terminateOperation(process, original)).isSameAs(original);
      assertThat(process.isAlive()).isFalse();
      assertThat(Files.readString(restored)).isEqualTo("canonical-restored");
    } finally {
      BoundedProcessTree.terminate(process, Duration.ofSeconds(10));
    }
  }

  @Test
  void acceptsFreshCreationAndVerifiedRetryOnlyWithRemovedAuthority() {
    for (String outcome : List.of("CREATED", "VERIFIED")) {
      assertThatCode(() -> DefaultSpacePreparationJourney.requireReceipt(
          receipt(outcome), CANDIDATE, SPEC, outcome)).doesNotThrowAnyException();
    }
  }

  @Test
  void rejectsMissingAccessAndIncompleteCleanupWithoutTrustingTruthyStrings() {
    for (String field : List.of("ownerAccessVerified", "filesReady", "calendarReady",
        "bootstrapAuthorityAbsent", "bootstrapMountAbsent", "tokenAbsent",
        "canonicalImageUnchanged", "supportSafe")) {
      ObjectNode missing = receipt("CREATED");
      missing.remove(field);
      assertDenied(missing);
      assertDenied(receipt("CREATED").put(field, false));
      assertDenied(receipt("CREATED").put(field, "true"));
    }
  }

  @Test
  void rejectsDifferentCandidateSpecSpaceOutcomeOrUnsafeEvidence() {
    for (String field : List.of("schemaVersion", "candidateCommit", "specificationCommit",
        "status", "spaceRef", "outcome")) {
      assertDenied(receipt("CREATED").put(field, "unexpected"));
    }
    ObjectNode missingSafety = receipt("CREATED");
    missingSafety.remove("containsSecretValues");
    assertDenied(missingSafety);
    assertDenied(receipt("CREATED").put("containsSecretValues", true));
    assertDenied(receipt("CREATED").put("containsSecretValues", "false"));
  }

  private static void assertDenied(ObjectNode receipt) {
    assertThatThrownBy(() -> DefaultSpacePreparationJourney.requireReceipt(
        receipt, CANDIDATE, SPEC, "CREATED")).isInstanceOf(ProductFlowException.class);
  }

  private static ObjectNode receipt(String outcome) {
    ObjectNode receipt = JsonMapper.builder().build().createObjectNode();
    receipt.put("schemaVersion", "weave.default-space-preparation/v1");
    receipt.put("candidateCommit", CANDIDATE);
    receipt.put("specificationCommit", SPEC);
    receipt.put("status", "passed");
    receipt.put("spaceRef", "workspace-default");
    receipt.put("outcome", outcome);
    for (String field : List.of("ownerAccessVerified", "filesReady", "calendarReady",
        "bootstrapAuthorityAbsent", "bootstrapMountAbsent", "tokenAbsent",
        "canonicalImageUnchanged", "supportSafe")) {
      receipt.put(field, true);
    }
    return receipt.put("containsSecretValues", false);
  }
}
