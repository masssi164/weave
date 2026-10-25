package com.massimotter.weave.backend.identity.migration;

import com.massimotter.weave.backend.agentruntime.adapter.ClientSecretKeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Explicit one-shot operator command; it never starts Spring or opens a listening port. */
public final class KeycloakRealmMigrationCli {
  private static final String CREDENTIAL_REF =
      "credentialref://weave/keycloak/realm-migration/bootstrap-admin";

  private KeycloakRealmMigrationCli() {}

  public static void main(String[] arguments) {
    int status = run(arguments, System.out, System.err);
    if (status != 0) {
      System.exit(status);
    }
  }

  static int run(String[] arguments, PrintStream output, PrintStream errors) {
    try {
      Arguments parsed = Arguments.parse(arguments);
      ObjectMapper mapper =
          tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
      KeycloakRealmMigrationManifestReader.MigrationBundle bundle =
          new KeycloakRealmMigrationManifestReader(mapper)
              .read(
                  parsed.artifactRoot(),
                  parsed.manifestDigest(),
                  parsed.baselineDigest(),
                  parsed.targetRevision());
      KeycloakRealmMigrationBackupProofReader.BackupProof backupProof =
          new KeycloakRealmMigrationBackupProofReader(mapper)
              .read(
                  parsed.backupProofFile(),
                  bundle,
                  parsed.environment(),
                  parsed.candidateCommit(),
                  parsed.composeProject());
      KeycloakRealmMigrationReceiptWriter receiptWriter =
          new KeycloakRealmMigrationReceiptWriter(mapper);
      receiptWriter.requireTargetAvailable(parsed.artifactRoot());

      KeycloakRealmMigrationSecretRefAccess secret =
          new KeycloakRealmMigrationSecretRefAccess(
              CREDENTIAL_REF, parsed.bootstrapSecretFile());
      KeycloakAdminAccessTokenProvider tokens =
          new ClientSecretKeycloakAdminAccessTokenProvider(
              new ClientSecretKeycloakAdminAccessTokenProvider.Settings(
                  parsed.keycloakBaseUrl(),
                  KeycloakFgapMigrationContract.BOOTSTRAP_REALM,
                  KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID,
                  CREDENTIAL_REF,
                  parsed.timeout()),
              secret,
              mapper);
      String accessToken = tokens.accessToken();
      RestClient restClient = restClient(parsed, accessToken);
      KeycloakFgapMigrationExecutor.MigrationResult result =
          new KeycloakFgapMigrationExecutor(
                  new KeycloakRealmMigrationTransport(restClient, mapper), mapper)
              .execute(bundle, backupProof);
      receiptWriter.write(parsed.artifactRoot(), result);
      output.println(mapper.writeValueAsString(result));
      return 0;
    } catch (KeycloakRealmMigrationException failure) {
      errors.println("keycloak-realm-migration: blocked [reason=" + failure.reasonCode() + "]");
      return 2;
    } catch (RuntimeWorkloadIdentityException failure) {
      errors.println("keycloak-realm-migration: blocked [reason=bootstrap-secret-authentication-failed]");
      return 2;
    } catch (Exception failure) {
      errors.println("keycloak-realm-migration: blocked [reason=unexpected-failure]");
      return 2;
    }
  }

  private static RestClient restClient(Arguments arguments, String accessToken) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(arguments.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(arguments.timeout());
    return RestClient.builder()
        .baseUrl(arguments.keycloakBaseUrl().toString())
        .requestFactory(requestFactory)
        .defaultHeaders(headers -> headers.setBearerAuth(accessToken))
        .build();
  }

  private record Arguments(
      Path artifactRoot,
      String manifestDigest,
      String baselineDigest,
      String targetRevision,
      Path backupProofFile,
      String environment,
      String candidateCommit,
      String composeProject,
      URI keycloakBaseUrl,
      Path bootstrapSecretFile,
      Duration timeout) {
    private static final Set<String> ALLOWED =
        Set.of(
            "artifact-root",
            "manifest-digest",
            "baseline-digest",
            "target-revision",
            "backup-proof-file",
            "environment",
            "candidate-commit",
            "compose-project",
            "keycloak-base-url",
            "bootstrap-secret-file",
            "timeout");

    static Arguments parse(String[] arguments) {
      if (arguments == null) {
        throw new KeycloakRealmMigrationException("operator-arguments-invalid");
      }
      Map<String, String> values = new LinkedHashMap<>();
      for (String argument : arguments) {
        if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
          throw new KeycloakRealmMigrationException("operator-arguments-invalid");
        }
        int separator = argument.indexOf('=');
        String name = argument.substring(2, separator);
        String value = argument.substring(separator + 1);
        if (!ALLOWED.contains(name) || value.isBlank() || values.putIfAbsent(name, value) != null) {
          throw new KeycloakRealmMigrationException("operator-arguments-invalid");
        }
      }
      Path artifactRoot = absolutePath(required(values, "artifact-root"));
      Path backupProofFile = absolutePath(required(values, "backup-proof-file"));
      Path bootstrapSecretFile = absolutePath(required(values, "bootstrap-secret-file"));
      URI baseUrl = httpUri(required(values, "keycloak-base-url"), true);
      Duration timeout = duration(values.getOrDefault("timeout", "PT10S"));
      return new Arguments(
          artifactRoot,
          required(values, "manifest-digest"),
          required(values, "baseline-digest"),
          required(values, "target-revision"),
          backupProofFile,
          required(values, "environment"),
          required(values, "candidate-commit"),
          required(values, "compose-project"),
          baseUrl,
          bootstrapSecretFile,
          timeout);
    }

    private static String required(Map<String, String> values, String name) {
      String value = values.get(name);
      if (value == null || value.isBlank()) {
        throw new KeycloakRealmMigrationException("operator-arguments-invalid");
      }
      return value;
    }

    private static Path absolutePath(String value) {
      Path path = Path.of(value).normalize();
      if (!path.isAbsolute()) {
        throw new KeycloakRealmMigrationException("operator-arguments-invalid");
      }
      return path;
    }

    private static URI httpUri(String value, boolean rootOnly) {
      URI uri;
      try {
        uri = URI.create(value);
      } catch (RuntimeException failure) {
        throw new KeycloakRealmMigrationException("operator-arguments-invalid");
      }
      String path = uri.getPath();
      if (uri.getHost() == null
          || !("http".equalsIgnoreCase(uri.getScheme())
              || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || (rootOnly && path != null && !path.isEmpty() && !"/".equals(path))) {
        throw new KeycloakRealmMigrationException("operator-arguments-invalid");
      }
      return uri;
    }

    private static Duration duration(String value) {
      try {
        Duration result = Duration.parse(value);
        if (result.isNegative()
            || result.isZero()
            || result.compareTo(Duration.ofMinutes(2)) > 0) {
          throw new KeycloakRealmMigrationException("operator-arguments-invalid");
        }
        return result;
      } catch (KeycloakRealmMigrationException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        throw new KeycloakRealmMigrationException("operator-arguments-invalid");
      }
    }
  }
}
