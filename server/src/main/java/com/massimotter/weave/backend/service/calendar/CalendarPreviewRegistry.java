package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Bounded, process-local leases for provider events that have no durable Weave reference. */
final class CalendarPreviewRegistry {
    static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_LEASES = 20_000;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Lease> leases = new HashMap<>();

    CalendarPreviewRegistry() { this(Clock.systemUTC(), new SecureRandom()); }

    CalendarPreviewRegistry(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    synchronized Issued issue(String organization, String principal, String calendarId, CalendarScope scope,
            long bindingRevision, EventId providerId, String providerVersion) {
        Instant now = clock.instant();
        leases.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (leases.size() >= MAX_LEASES) throw new IllegalStateException("Calendar preview capacity exhausted");
        byte[] bytes = new byte[24];
        String handle;
        do {
            random.nextBytes(bytes);
            handle = "pv_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (leases.containsKey(handle));
        Instant expiresAt = now.plus(LIFETIME);
        leases.put(handle, new Lease(organization, principal, calendarId, scope, bindingRevision,
                providerId, providerVersion, expiresAt));
        return new Issued(handle, expiresAt);
    }

    synchronized Optional<Lease> resolve(String handle) {
        if (handle == null || !handle.matches("pv_[A-Za-z0-9_-]{32}")) return Optional.empty();
        Lease lease = leases.get(handle);
        if (lease == null) return Optional.empty();
        if (!lease.expiresAt().isAfter(clock.instant())) {
            leases.remove(handle);
            return Optional.empty();
        }
        return Optional.of(lease);
    }

    record Issued(String handle, Instant expiresAt) {}

    record Lease(String organization, String principal, String calendarId, CalendarScope scope,
            long bindingRevision, EventId providerId, String providerVersion, Instant expiresAt) {}
}
