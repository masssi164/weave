package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.api.FilesUserApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.FilesUserItemResponse;
import com.massimotter.weave.userapi.model.FilesUserListResponse;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real Files User contract proof through the same generated JVM client used by other consumers. */
final class GeneratedFilesJourney {
  private static final Pattern SAFE_ERROR_CODE =
      Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([a-z][a-z0-9-]{0,63})\\\"");
  private final FilesUserApi files;

  GeneratedFilesJourney(ProductFlowEnvironment environment) {
    ApiClient client =
        new ApiClient()
            .setHttpClientBuilder(
                HttpClient.newBuilder()
                    .sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER))
            .setReadTimeout(Duration.ofSeconds(30));
    client.updateBaseUri(
        environment.apiOrigin().getScheme()
            + "://"
            + environment.apiOrigin().getRawAuthority());
    files = new FilesUserApi(client);
  }

  Proof createAndVerify(String memberToken, String outsiderToken, String runId) {
    String suffix = Hashing.sha256(runId).substring(0, 20);
    String name = "generated-files-" + suffix + ".bin";
    String idempotencyKey = "generated-files-upload-" + suffix;
    byte[] content = new byte[] {0, 10, (byte) 0xff, 34, 92, 127};
    Path source = null;
    String stage = "root-list";
    try {
      source = Files.createTempFile("weave-user-files-", ".bin");
      Files.write(source, content);
      FilesUserListResponse root = files.listFilesItems(null, bearer(memberToken));
      if (!"file:root".equals(root.getParentFileId())
          || !root.getAllowedActions().contains("upload")) {
        throw new ProductFlowException("generated Files root is not writable for the member");
      }
      stage = "upload";
      FilesUserItemResponse created =
          files.uploadFilesItemContent(
              root.getParentFileId(), name, "*", idempotencyKey, source.toFile(),
              "application/octet-stream", bearer(memberToken));
      stage = "idempotent-replay";
      FilesUserItemResponse replayed =
          files.uploadFilesItemContent(
              root.getParentFileId(), name, "*", idempotencyKey, source.toFile(),
              "application/octet-stream", bearer(memberToken));
      if (created == null || !created.getFileId().equals(replayed.getFileId())
          || !created.getRevision().equals(replayed.getRevision())) {
        throw new ProductFlowException("generated Files upload did not replay its stable identity");
      }
      stage = "idempotency-conflict";
      Files.write(source, new byte[] {42});
      try {
        files.uploadFilesItemContent(
            root.getParentFileId(), name, "*", idempotencyKey, source.toFile(),
            "application/octet-stream", bearer(memberToken));
        throw new ProductFlowException("generated Files accepted changed content under the same key");
      } catch (ApiException conflict) {
        if (conflict.getCode() != 409) {
          throw conflict;
        }
      }
      Proof proof = new Proof(created.getFileId(), name, content);
      verify(proof, memberToken, outsiderToken);
      return proof;
    } catch (ApiException failure) {
      throw new ProductFlowException(
          "generated Files " + stage + " failed with HTTP " + failure.getCode()
              + " code=" + safeErrorCode(failure));
    } catch (IOException failure) {
      throw new ProductFlowException("generated Files local proof file failed");
    } finally {
      if (source != null) {
        try {
          Files.deleteIfExists(source);
        } catch (IOException ignored) {
          // The isolated E2E process and stack are discarded after this run.
        }
      }
    }
  }

  void verify(Proof proof, String memberToken, String outsiderToken) {
    try {
      FilesUserListResponse root = files.listFilesItems(null, bearer(memberToken));
      if (root == null || root.getItems().stream()
          .noneMatch(item -> proof.fileId().equals(item.getFileId())
              && proof.name().equals(item.getName()))) {
        throw new ProductFlowException("generated Files identity was absent from the member list");
      }
      FilesUserItemResponse inspected = files.getFilesItem(proof.fileId(), bearer(memberToken));
      if (inspected == null || !proof.fileId().equals(inspected.getFileId())
          || !inspected.getAllowedActions().contains("download")) {
        throw new ProductFlowException("generated Files identity was not downloadable by its owner");
      }
      var response = files.downloadFilesItemContentWithHttpInfo(proof.fileId(), null, bearer(memberToken));
      File downloaded = response.getData();
      String expectedEtag = "\"sha256-50de86022dd7b26e4724b3dd79b3c63719169aa289ceb99e8041ba6ef44f4e95\"";
      try {
        if (response.getStatusCode() != 200
            || !expectedEtag.equals(header(response.getHeaders(), "ETag"))
            || !"sha-256=:UN6GAi3Xsm5HJLPdebPGNxkWmqKJzrmegEG6bvRPTpU=:"
                .equals(header(response.getHeaders(), "Content-Digest"))
            || !"6".equals(header(response.getHeaders(), "Content-Length"))
            || !"application/octet-stream".equals(header(response.getHeaders(), "Content-Type"))) {
          throw new ProductFlowException("generated Files download violated its binary response contract");
        }
        if (downloaded == null || !Arrays.equals(proof.content(), Files.readAllBytes(downloaded.toPath()))) {
          throw new ProductFlowException("generated Files download changed the uploaded bytes");
        }
      } finally {
        if (downloaded != null) {
          Files.deleteIfExists(downloaded.toPath());
        }
      }
      try {
        File unexpected = files.downloadFilesItemContent(proof.fileId(), expectedEtag, bearer(memberToken));
        if (unexpected != null) Files.deleteIfExists(unexpected.toPath());
        throw new ProductFlowException("generated Files conditional download did not return 304");
      } catch (ApiException unchanged) {
        if (unchanged.getCode() != 304
            || !expectedEtag.equals(header(unchanged.getResponseHeaders().map(), "ETag"))) {
          throw new ProductFlowException("generated Files conditional download violated its validator contract");
        }
      }
      try {
        files.getFilesItem(proof.fileId(), bearer(outsiderToken));
      } catch (ApiException denial) {
        if (denial.getCode() == 403 || denial.getCode() == 404) {
          return;
        }
        throw new ProductFlowException(
            "generated Files outsider denial returned HTTP " + denial.getCode());
      }
      throw new ProductFlowException("generated Files exposed the member object to an outsider");
    } catch (ApiException failure) {
      throw new ProductFlowException(
          "generated Files verification failed with HTTP " + failure.getCode());
    } catch (IOException failure) {
      throw new ProductFlowException("generated Files download could not be verified");
    }
  }

  private static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token);
  }

  private static String header(Map<String, List<String>> headers, String name) {
    return headers.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst().orElse("");
  }

  private static String safeErrorCode(ApiException failure) {
    String body = failure.getResponseBody();
    if (body == null || body.length() > 8192) {
      return "unavailable";
    }
    Matcher matched = SAFE_ERROR_CODE.matcher(body);
    return matched.find() ? matched.group(1) : "unavailable";
  }

  // The disposable Compose topology tears down its exact volumes after this run; the User API
  // does not yet expose deletion, so this proof object must never be written to a live tenant.
  record Proof(String fileId, String name, byte[] content) {}
}
