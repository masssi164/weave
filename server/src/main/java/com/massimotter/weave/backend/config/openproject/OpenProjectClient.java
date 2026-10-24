package com.massimotter.weave.backend.config.openproject;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PatchExchange;
import tools.jackson.databind.JsonNode;

/** The only HTTP verbs needed by OpenProject board synchronization. */
public interface OpenProjectClient {
  @GetExchange
  JsonNode get(URI url, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

  @PatchExchange(contentType = "application/json")
  JsonNode patch(URI url, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody Map<String, Object> body);
}
