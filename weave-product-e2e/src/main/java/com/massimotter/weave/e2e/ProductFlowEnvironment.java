package com.massimotter.weave.e2e;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated, secret-free addressing contract for one isolated product-flow run. */
record ProductFlowEnvironment(
    String runId,
    URI apiOrigin,
    URI issuer,
    URI mailpitApi,
    URI mcpEndpoint,
    Path caCertificate,
    Path tlsLeafCertificate,
    Path hostsFile,
    Path bootstrapOwnerToken,
    Path workloadCredentialRoot,
    Path evidenceFile,
    Duration convergenceTimeout) {

  private static final Pattern RUN_ID =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,159}");

  ProductFlowEnvironment {
    if (runId == null || !RUN_ID.matcher(runId).matches()) {
      throw new IllegalArgumentException("weave.e2e.run-id is invalid");
    }
    apiOrigin = requireHttpsOrigin(apiOrigin, "weave.e2e.api-origin");
    issuer = requireHttps(issuer, "weave.e2e.issuer");
    mailpitApi = requireLoopbackHttp(mailpitApi, "weave.e2e.mailpit-api");
    mcpEndpoint = requireHttps(mcpEndpoint, "weave.e2e.mcp-endpoint");
    caCertificate = requirePrivateInput(caCertificate, "weave.e2e.ca-certificate");
    tlsLeafCertificate =
        requirePrivateInput(tlsLeafCertificate, "weave.e2e.tls-leaf-certificate");
    hostsFile = requirePrivateInput(hostsFile, "weave.e2e.hosts-file");
    requireExactLoopbackMappings(
        hostsFile,
        new HashSet<>(
            List.of(apiOrigin.getHost(), issuer.getHost(), mcpEndpoint.getHost())));
    bootstrapOwnerToken =
        requirePrivateInput(bootstrapOwnerToken, "weave.e2e.bootstrap-owner-token");
    workloadCredentialRoot =
        requireDirectory(workloadCredentialRoot, "weave.e2e.workload-credential-root");
    evidenceFile =
        Objects.requireNonNull(evidenceFile, "weave.e2e.evidence-file")
            .toAbsolutePath()
            .normalize();
    if (evidenceFile.getParent() == null) {
      throw new IllegalArgumentException("weave.e2e.evidence-file needs a parent directory");
    }
    if (convergenceTimeout == null
        || convergenceTimeout.compareTo(Duration.ofSeconds(30)) < 0
        || convergenceTimeout.compareTo(Duration.ofMinutes(10)) > 0) {
      throw new IllegalArgumentException(
          "weave.e2e.convergence-timeout must be between 30 seconds and 10 minutes");
    }
  }

  static ProductFlowEnvironment fromSystemProperties() {
    return new ProductFlowEnvironment(
        required("run-id"),
        URI.create(required("api-origin")),
        URI.create(required("issuer")),
        URI.create(required("mailpit-api")),
        URI.create(required("mcp-endpoint")),
        Path.of(required("ca-certificate")),
        Path.of(required("tls-leaf-certificate")),
        Path.of(required("hosts-file")),
        Path.of(required("bootstrap-owner-token")),
        Path.of(required("workload-credential-root")),
        Path.of(required("evidence-file")),
        Duration.parse(System.getProperty("weave.e2e.convergence-timeout", "PT2M")));
  }

  URI api(String path) {
    if (path == null || !path.startsWith("/") || path.startsWith("//")) {
      throw new IllegalArgumentException("API paths must be absolute origin-relative paths");
    }
    return apiOrigin.resolve(path);
  }

  URI oidc(String suffix) {
    if (suffix == null || !suffix.startsWith("/")) {
      throw new IllegalArgumentException("OIDC suffix must be absolute");
    }
    return URI.create(issuer.toString().replaceAll("/+$", "") + suffix);
  }

  String ownerEmail() {
    return actorEmail("owner");
  }

  String memberEmail() {
    return actorEmail("member");
  }

  private String actorEmail(String actor) {
    return "weave-e2e-" + Hashing.sha256(runId).substring(0, 20) + "-" + actor
        + "@example.invalid";
  }

  private static String required(String name) {
    String value = System.getProperty("weave.e2e." + name, "").trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Missing -Dweave.e2e." + name);
    }
    return value;
  }

  private static URI requireHttpsOrigin(URI uri, String name) {
    URI validated = requireHttps(uri, name);
    if (!validated.getPath().isEmpty() && !"/".equals(validated.getPath())) {
      throw new IllegalArgumentException(name + " must not contain a path");
    }
    return URI.create(
        validated.getScheme() + "://" + validated.getRawAuthority() + "/");
  }

  private static URI requireHttps(URI uri, String name) {
    if (uri == null
        || !"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException(name + " must be a credential-free HTTPS URI");
    }
    return uri;
  }

  private static URI requireLoopbackHttp(URI uri, String name) {
    if (uri == null
        || !"http".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException(name + " must be a credential-free loopback HTTP URI");
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    if (!host.equals("127.0.0.1") && !host.equals("localhost") && !host.equals("::1")) {
      throw new IllegalArgumentException(name + " must stay on loopback");
    }
    return uri;
  }

  private static Path requirePrivateInput(Path value, String name) {
    Path path = Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    if (Files.isSymbolicLink(path)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(name + " must be a regular non-symlink file");
    }
    return path;
  }

  private static Path requireDirectory(Path value, String name) {
    Path path = Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    if (Files.isSymbolicLink(path)
        || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(name + " must be a non-symlink directory");
    }
    return path;
  }

  private static void requireExactLoopbackMappings(Path path, Set<String> expectedHosts) {
    Set<String> observedHosts = new HashSet<>();
    try {
      for (String rawLine : Files.readAllLines(path)) {
        String line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] fields = line.split("\\s+");
        if (fields.length < 2
            || (!"127.0.0.1".equals(fields[0]) && !"::1".equals(fields[0]))) {
          throw new IllegalArgumentException(
              "weave.e2e.hosts-file may contain only explicit loopback mappings");
        }
        for (int index = 1; index < fields.length; index++) {
          String host = fields[index].toLowerCase(Locale.ROOT);
          if (!expectedHosts.contains(host)) {
            throw new IllegalArgumentException(
                "weave.e2e.hosts-file contains an unexpected host");
          }
          observedHosts.add(host);
        }
      }
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException(
          "weave.e2e.hosts-file could not be validated", failure);
    }
    if (!observedHosts.equals(expectedHosts)) {
      throw new IllegalArgumentException(
          "weave.e2e.hosts-file must map every exact product host to loopback");
    }
  }
}
