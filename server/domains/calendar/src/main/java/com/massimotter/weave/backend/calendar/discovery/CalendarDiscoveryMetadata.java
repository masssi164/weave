package com.massimotter.weave.backend.calendar.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategorySelectionPolicy;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by calendar; shared policy contains no feature catalog. */
public final class CalendarDiscoveryMetadata {
    private CalendarDiscoveryMetadata() {}

    public static ProviderCategoryFragment calendarCategory() {
        return new ProviderCategoryFragment("calendar",
                2,
                "calendar",
                "calendar",
                "calendar",
                true,
                null,
                ProviderRealityLevel.RELEASE_READY,
                Set.of(ProviderModule.CALENDAR),
                ProviderCategorySelectionPolicy.boundMetadataOnly(),
                new ProviderCategoryContract(
                    List.of("calendar.read", "calendar.manage_events", "calendar.thread_refs"),
                    List.of("weave-native"),
                    List.of("nextcloud-caldav", "microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav"),
                    List.of("WeaveCalendar", "WeaveEvent", "WeaveRecurrence", "WeaveAttendee", "WeaveResource", "WeaveAvailability", "ProviderRef"),
                    "workspace/team/channel calendar source is selected by admin; private personal calendars are not the default product source",
                    List.of("RRULE fidelity", "time zones", "resource booking", "attendee response semantics", "online meeting links"),
                    "export iCalendar-compatible events where possible; deletion follows calendar retention and legal hold policy",
                    "calendar replacement requires recurrence/time-zone/resource dry-run and attendee impact report"));
    }

    public static CanonicalDomainContribution calendarDomain() {
        return new CanonicalDomainContribution("calendar", 5, CanonicalDomainRegistry.entry(
                "calendar",
                "Calendar",
                "Workspace and shared calendar facade for events, occurrence series, recurrence exceptions, attendees, availability, and resources.",
                List.of("WeaveCalendar", "WeaveEvent", "WeaveRecurrence", "WeaveAttendee", "WeaveResource", "WeaveAvailability", "ProviderRef"),
                List.of(),
                CanonicalDomainRegistry.standardCapabilities("calendar"),
                Map.of(
                    "weave-native", "live_write",
                    "nextcloud-caldav", "release_ready",
                    "microsoft-graph-calendar", "contract_only",
                    "google-workspace-calendar", "contract_only",
                    "generic-caldav", "live_read")));
    }
}
