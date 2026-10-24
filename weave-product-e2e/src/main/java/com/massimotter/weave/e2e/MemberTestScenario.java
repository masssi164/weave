package com.massimotter.weave.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Uses the same secret-free recipe compiler as the simulator and Dogfood drivers. */
final class MemberTestScenario {
  private final JsonNode resolved;

  private MemberTestScenario(JsonNode resolved) {
    this.resolved = resolved;
  }

  static MemberTestScenario resolve(Path repository, String runId) {
    Process process = null;
    try {
      process = new ProcessBuilder("python3",
          repository.resolve("tools/member_test_scenario.py").toAbsolutePath().toString(),
          "--run-id", runId).start();
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new ProductFlowException("Shared member scenario resolution timed out");
      }
      byte[] output = process.getInputStream().readNBytes(16385);
      if (process.exitValue() != 0 || output.length > 16384) {
        throw new ProductFlowException("Shared member scenario resolution failed");
      }
      JsonNode value = JsonMapper.builder().build().readTree(new String(output, StandardCharsets.UTF_8));
      if (!"weave.member-client-scenario-resolved/v1".equals(value.path("schemaVersion").asString())
          || !runId.equals(value.path("runId").asString())
          || !value.path("scenarioSha256").asString().matches("[0-9a-f]{64}")) {
        throw new ProductFlowException("Shared member scenario identity is invalid");
      }
      return new MemberTestScenario(value);
    } catch (IOException failure) {
      throw new ProductFlowException("Shared member scenario compiler is unavailable", failure);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("Shared member scenario resolution was interrupted", failure);
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }

  JsonNode resolved() { return resolved; }

  String spaceRef() { return resolved.path("sharedSpaceRef").asString(); }

  String content(String field) {
    String value = resolved.path("content").path(field).asString("");
    if (value.isEmpty()) throw new ProductFlowException("Shared member content is unavailable");
    return value;
  }

  String actorLogicalId(String slot) {
    for (JsonNode actor : resolved.path("actors")) {
      if (slot.equals(actor.path("slot").asString())) return actor.path("logicalId").asString();
    }
    throw new ProductFlowException("Shared member actor slot is unavailable");
  }
}
