package com.massimotter.weave.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderObjectMapping;
import com.massimotter.weave.backend.portability.ProviderObjectMapping.ProviderRef;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalCollaborationDomainTest {

    @Test
    void filesPathsNormalizeWithoutAllowingTraversal() {
        assertThat(new FilePath("//team//plans/").value()).isEqualTo("/team/plans");
        assertThat(new FilePath("/team").child("plans.md").value()).isEqualTo("/team/plans.md");
        assertThatThrownBy(() -> new FilePath("/team/../private"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FilePath("/team").child("nested/name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recurrenceExpansionIsWindowBoundedAndKeepsLocalTimeAcrossDst() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        RecurrenceSet recurrence = new RecurrenceSet(
                RecurrenceFrequency.WEEKLY,
                1,
                3,
                null,
                List.of(),
                List.of());
        CalendarEvent event = new CalendarEvent(
                new CalendarId("calendar:workspace"),
                new EventId("event:planning"),
                CalendarScope.workspace(),
                "Planning",
                null,
                LocalDateTime.of(2026, 3, 22, 9, 0),
                LocalDateTime.of(2026, 3, 22, 10, 0),
                berlin,
                false,
                null,
                List.of(),
                recurrence,
                null,
                null);

        ZonedDateTime first = event.startsAt();
        ZonedDateTime afterDst = first.plusWeeks(2);
        assertThat(first.getHour()).isEqualTo(9);
        assertThat(afterDst.getHour()).isEqualTo(9);
        assertThat(first.getOffset()).isNotEqualTo(afterDst.getOffset());

        assertThat(new RecurrenceSet(
                RecurrenceFrequency.DAILY,
                1,
                null,
                null,
                List.of(),
                List.of()).rrule()).isEqualTo("FREQ=DAILY");
    }

    @Test
    void providerMappingsExposeOnlyHashedSupportReferences() {
        ProviderObjectMapping mapping = new ProviderObjectMapping(
                "files",
                "file:canonical-1",
                new ProviderRef("sharepoint", "drive/raw-secret-shaped-id"),
                "version-7",
                MappingClass.PORTABLE,
                List.of());

        assertThat(mapping.providerRef().supportSafeRef())
                .startsWith("provider-ref:sha256:")
                .doesNotContain("sharepoint")
                .doesNotContain("raw-secret-shaped-id");
    }
}
