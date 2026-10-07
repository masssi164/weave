package com.massimotter.weave.backend.service.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CalendarPreviewRegistryTest {
    @Test
    void opaqueLeaseExpiresAndRejectsMalformedHandles() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-06T12:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        CalendarPreviewRegistry registry = new CalendarPreviewRegistry(clock, new SecureRandom());
        CalendarPreviewRegistry.Issued issued = registry.issue("tenant", "member", "calendar:scope",
                CalendarScope.workspace(), 7, new EventId("private-provider-id"), "\"etag-1\"");
        assertThat(issued.handle()).matches("pv_[A-Za-z0-9_-]{32}").doesNotContain("private-provider-id");
        assertThat(registry.resolve(issued.handle())).isPresent();
        assertThat(registry.resolve(issued.handle() + "x")).isEmpty();
        now.set(issued.expiresAt());
        assertThat(registry.resolve(issued.handle())).isEmpty();
    }
}
