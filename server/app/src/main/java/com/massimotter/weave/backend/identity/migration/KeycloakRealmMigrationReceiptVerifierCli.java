package com.massimotter.weave.backend.identity.migration;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

/** Secret-free Compose gate for the exact manifest-bound Keycloak migration receipt. */
public final class KeycloakRealmMigrationReceiptVerifierCli {
  private KeycloakRealmMigrationReceiptVerifierCli() {}

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
      new KeycloakRealmMigrationReceiptVerifier(mapper)
          .verify(parsed.artifactRoot(), bundle, backupProof);
      output.println("keycloak-realm-migration-receipt: verified");
      return 0;
    } catch (KeycloakRealmMigrationException failure) {
      errors.println(
          "keycloak-realm-migration-receipt: blocked [reason="
              + failure.reasonCode()
              + "]");
      return 2;
    } catch (Exception failure) {
      errors.println("keycloak-realm-migration-receipt: blocked [reason=unexpected-failure]");
      return 2;
    }
  }

  private record Arguments(
      Path artifactRoot,
      String manifestDigest,
      String baselineDigest,
      String targetRevision,
      Path backupProofFile,
      String environment,
      String candidateCommit,
      String composeProject) {
    private static final Set<String> ALLOWED =
        Set.of(
            "artifact-root",
            "manifest-digest",
            "baseline-digest",
            "target-revision",
            "backup-proof-file",
            "environment",
            "candidate-commit",
            "compose-project");

    static Arguments parse(String[] arguments) {
      if (arguments == null) {
        throw blocked();
      }
      Map<String, String> values = new LinkedHashMap<>();
      for (String argument : arguments) {
        if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
          throw blocked();
        }
        int separator = argument.indexOf('=');
        String name = argument.substring(2, separator);
        String value = argument.substring(separator + 1);
        if (!ALLOWED.contains(name) || value.isBlank() || values.putIfAbsent(name, value) != null) {
          throw blocked();
        }
      }
      Path artifactRoot = absolutePath(required(values, "artifact-root"));
      Path backupProofFile = absolutePath(required(values, "backup-proof-file"));
      return new Arguments(
          artifactRoot,
          required(values, "manifest-digest"),
          required(values, "baseline-digest"),
          required(values, "target-revision"),
          backupProofFile,
          required(values, "environment"),
          required(values, "candidate-commit"),
          required(values, "compose-project"));
    }

    private static String required(Map<String, String> values, String name) {
      String value = values.get(name);
      if (value == null || value.isBlank()) {
        throw blocked();
      }
      return value;
    }

    private static Path absolutePath(String value) {
      Path path = Path.of(value).normalize();
      if (!path.isAbsolute()) {
        throw blocked();
      }
      return path;
    }

    private static KeycloakRealmMigrationException blocked() {
      return new KeycloakRealmMigrationException("operator-arguments-invalid");
    }
  }
}
