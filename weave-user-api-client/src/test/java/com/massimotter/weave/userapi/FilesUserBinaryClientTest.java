package com.massimotter.weave.userapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.massimotter.weave.userapi.api.FilesUserApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the generated transport sends the declared octet stream, not a JSON File value. */
class FilesUserBinaryClientTest {
  @TempDir Path temporaryDirectory;

  @Test
  void uploadAndUpdateSendExactFileBytes() throws Exception {
    byte[] content = new byte[] {0, 10, (byte) 0xff, 34, 92, 127};
    Path source = temporaryDirectory.resolve("binary-proof");
    Files.write(source, content);
    List<ReceivedRequest> received = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/files/items",
        exchange -> {
          try (exchange) {
            received.add(
                new ReceivedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(204, -1);
          }
        });
    server.start();
    try {
      ApiClient client = new ApiClient();
      client.updateBaseUri("http://127.0.0.1:" + server.getAddress().getPort());
      FilesUserApi api = new FilesUserApi(client);
      api.uploadFilesItemContent("root", "binary-proof", "*", "binary-upload-proof-0001", source.toFile(), "application/octet-stream");
      api.updateFilesItemContent("file-1", "\"strong-revision\"", "binary-update-proof-0001", source.toFile(), "application/octet-stream");
    } finally {
      server.stop(0);
    }

    assertEquals(2, received.size());
    assertEquals("POST", received.get(0).method());
    assertEquals("/api/files/items/uploads", received.get(0).path());
    assertEquals("PUT", received.get(1).method());
    assertEquals("/api/files/items/file-1/content", received.get(1).path());
    for (ReceivedRequest request : received) {
      assertEquals("application/octet-stream", request.contentType());
      assertArrayEquals(content, request.body());
    }
  }

  private record ReceivedRequest(String method, String path, String contentType, byte[] body) {}
}
