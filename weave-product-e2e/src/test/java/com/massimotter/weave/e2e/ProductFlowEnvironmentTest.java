package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductFlowEnvironmentTest {
  @TempDir Path temporaryDirectory;

  @Test
  void normalizesTheIsolatedAddressingContractWithoutExposingTheRunIdInEmails()
      throws Exception {
    ProductFlowEnvironment environment =
        environment(
            URI.create("https://api.weave.test:44443"),
            URI.create("https://auth.weave.test:44443/realms/weave"),
            URI.create("http://127.0.0.1:38025/api/v1"),
            Duration.ofMinutes(2));

    assertThat(environment.productOrigin()).isEqualTo(URI.create("https://weave.test:44443/"));
    assertThat(environment.apiOrigin()).isEqualTo(URI.create("https://api.weave.test:44443/"));
    assertThat(environment.tenantId()).isEqualTo("tenant-default");
    assertThat(environment.api("/api/profile/readiness"))
        .isEqualTo(URI.create("https://api.weave.test:44443/api/profile/readiness"));
    assertThat(environment.oidc("/protocol/openid-connect/token"))
        .isEqualTo(
            URI.create(
                "https://auth.weave.test:44443/realms/weave/protocol/openid-connect/token"));
    assertThat(environment.ownerEmail())
        .matches("weave-e2e-[a-f0-9]{20}-owner@example\\.invalid")
        .doesNotContain(environment.runId());
    assertThat(environment.memberEmail())
        .matches("weave-e2e-[a-f0-9]{20}-member@example\\.invalid")
        .doesNotContain(environment.runId());
  }

  @Test
  void rejectsAnExternalMailReaderAndAnInsecureProductEndpoint() throws Exception {
    assertThatThrownBy(
            () ->
                environment(
                    URI.create("http://api.weave.test:44080"),
                    URI.create("https://auth.weave.test:44443/realms/weave"),
                    URI.create("http://127.0.0.1:38025/api/v1"),
                    Duration.ofMinutes(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("credential-free HTTPS URI");

    assertThatThrownBy(
            () ->
                environment(
                    URI.create("https://api.weave.test:44443"),
                    URI.create("https://auth.weave.test:44443/realms/weave"),
                    URI.create("http://mail.weave.test:38025/api/v1"),
                    Duration.ofMinutes(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("loopback");
  }

  @Test
  void rejectsUnboundedConvergenceAndSymlinkedSecretInputs() throws Exception {
    assertThatThrownBy(
            () ->
                environment(
                    URI.create("https://api.weave.test:44443"),
                    URI.create("https://auth.weave.test:44443/realms/weave"),
                    URI.create("http://127.0.0.1:38025/api/v1"),
                    Duration.ofSeconds(20)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 30 seconds and 10 minutes");

    Path target = Files.writeString(temporaryDirectory.resolve("real-token"), "x".repeat(64));
    Path link = temporaryDirectory.resolve("linked-token");
    try {
      Files.createSymbolicLink(link, target.getFileName());
    } catch (UnsupportedOperationException unsupported) {
      return;
    }
    assertThatThrownBy(
            () ->
                new ProductFlowEnvironment(
                    "fixture-run-42",
                    "1".repeat(40),
                    "4".repeat(40),
                    "2".repeat(40),
                    "weave-e2e-0123456789abcdef",
                    "tenant-default",
                    URI.create("https://weave.test:44443"),
                    URI.create("https://api.weave.test:44443"),
                    URI.create("https://auth.weave.test:44443/realms/weave"),
                    URI.create("http://127.0.0.1:38025/api/v1"),
                    URI.create("https://api.weave.test:44443/mcp"),
                    URI.create("http://127.0.0.1:39025"),
                    input("ca.pem"),
                    input("leaf.pem"),
                    hosts("hosts"),
                    link,
                    input("chat-proof-link-test.token"),
                    Files.createDirectories(temporaryDirectory.resolve("credentials-link-test")),
                    temporaryDirectory.resolve("evidence-link-test.json"),
                    input("restart-command"),
                    temporaryDirectory.resolve("restart-evidence-link-test.json"),
                    "sha256:" + "3".repeat(64),
                    Duration.ofMinutes(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("regular non-symlink file");
  }

  @Test
  void rejectsAnUnsafeConfiguredTenantIdentifier() throws Exception {
    assertThatThrownBy(
            () ->
                environment(
                    URI.create("https://api.weave.test:44443"),
                    URI.create("https://auth.weave.test:44443/realms/weave"),
                    URI.create("http://127.0.0.1:38025/api/v1"),
                    Duration.ofMinutes(2),
                    "tenant with spaces"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("weave.e2e.tenant-id is invalid");
  }

  private ProductFlowEnvironment environment(
      URI apiOrigin, URI issuer, URI mailpit, Duration timeout) throws Exception {
    return environment(apiOrigin, issuer, mailpit, timeout, "tenant-default");
  }

  private ProductFlowEnvironment environment(
      URI apiOrigin, URI issuer, URI mailpit, Duration timeout, String tenantId) throws Exception {
    return new ProductFlowEnvironment(
        "fixture-run-42",
        "1".repeat(40),
        "4".repeat(40),
        "2".repeat(40),
        "weave-e2e-0123456789abcdef",
        tenantId,
        URI.create("https://weave.test:44443"),
        apiOrigin,
        issuer,
        mailpit,
        URI.create("https://api.weave.test:44443/mcp"),
        URI.create("http://127.0.0.1:39025"),
        input("ca-" + temporaryDirectory.toFile().list().length + ".pem"),
        input("leaf-" + temporaryDirectory.toFile().list().length + ".pem"),
        hosts("hosts-" + temporaryDirectory.toFile().list().length),
        input("bootstrap-" + temporaryDirectory.toFile().list().length + ".token"),
        input("chat-proof-" + temporaryDirectory.toFile().list().length + ".token"),
        Files.createDirectories(
            temporaryDirectory.resolve(
                "credentials-" + temporaryDirectory.toFile().list().length)),
        temporaryDirectory.resolve("evidence.json"),
        input("restart-command-" + temporaryDirectory.toFile().list().length),
        temporaryDirectory.resolve("persistence-restart-evidence.json"),
        "sha256:" + "3".repeat(64),
        timeout);
  }

  private Path input(String name) throws Exception {
    return Files.writeString(temporaryDirectory.resolve(name), "fixture");
  }

  private Path hosts(String name) throws Exception {
    return Files.writeString(
        temporaryDirectory.resolve(name),
        "127.0.0.1 weave.test api.weave.test auth.weave.test\n");
  }
}
