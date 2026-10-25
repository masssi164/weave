package com.massimotter.weave.backend.calendar.config.http;

import com.massimotter.weave.backend.calendar.config.CalendarCalDavProperties;
import java.net.http.HttpClient;
import java.time.Duration;

/** The CalDAV REPORT/PROPFIND wire protocol remains intentionally request-specific. */
public final class CalDavHttpConfig {
  private CalDavHttpConfig() {}

  public static HttpClient client(CalendarCalDavProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds())).build();
  }
}
